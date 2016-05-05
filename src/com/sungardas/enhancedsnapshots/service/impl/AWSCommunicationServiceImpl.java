package com.sungardas.enhancedsnapshots.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.AttachVolumeRequest;
import com.amazonaws.services.ec2.model.AttachVolumeResult;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.CreateSnapshotRequest;
import com.amazonaws.services.ec2.model.CreateSnapshotResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.DeleteSnapshotRequest;
import com.amazonaws.services.ec2.model.DeleteTagsRequest;
import com.amazonaws.services.ec2.model.DeleteVolumeRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSnapshotsRequest;
import com.amazonaws.services.ec2.model.DescribeSnapshotsResult;
import com.amazonaws.services.ec2.model.DescribeVolumesRequest;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.DetachVolumeRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Snapshot;
import com.amazonaws.services.ec2.model.SnapshotState;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Volume;
import com.amazonaws.services.ec2.model.VolumeState;
import com.amazonaws.services.ec2.model.VolumeType;
import com.sungardas.enhancedsnapshots.service.AWSCommunicationService;
import com.sungardas.enhancedsnapshots.service.SnapshotService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import static java.lang.String.format;

@Service
@Profile("prod")
public class AWSCommunicationServiceImpl implements AWSCommunicationService {

    private static final Logger LOG = LogManager.getLogger(AWSCommunicationServiceImpl.class);
    private final static int MIN_SIZE_OF_OI1_VOLUME = 4;
    private final static int MIN_IOPS_VALUE = 100;
    private final static int MAX_IOPS_VALUE = 20_000;

    @Autowired
    private SnapshotService snapshotService;

    @Autowired
    private AmazonEC2 ec2client;

    @Value("${sungardas.worker.configuration}")
    private String configurationId;

    @Value("${sungardas.restore.snapshot.attempts:30}")
    private int retryRestoreAttempts;

    @Value("${sungardas.restore.snapshot.timeout:30}")
    private int retryRestoreTimeout;

   @Override
   public List<AvailabilityZone> describeAvailabilityZonesForCurrentRegion() {
       return ec2client.describeAvailabilityZones().getAvailabilityZones();
   }

    @Override
    public String getCurrentAvailabilityZone() {
        return getInstance(configurationId).getPlacement()
                .getAvailabilityZone();    }

    @Override
    public void createTemporaryTag(String resourceId, String description) {
        CreateTagsRequest tagsRequest = new CreateTagsRequest().withResources(resourceId).withTags(
                new Tag().withKey("ESTempVolume").withValue(description));
        ec2client.createTags(tagsRequest);
    }

    @Override
    public void deleteTemporaryTag(String resourceId) {
        List<Volume> volumes= ec2client.describeVolumes(new DescribeVolumesRequest().withVolumeIds(resourceId)).getVolumes();
        if(volumes.size()>0) {
            Volume volume = volumes.get(0);
            List<Tag> tags = volume.getTags();
            boolean tagWasDeleted = false;
            for(Tag tag: tags) {
                if (tag.getKey().equals("ESTempVolume")) {
                    DeleteTagsRequest tagsRequest = new DeleteTagsRequest().withResources(resourceId).withTags();
                    ec2client.deleteTags(tagsRequest);
                    tagWasDeleted = true;
                    break;
                }
            }
            if(! tagWasDeleted) {
                LOG.info("No temporary tag associated with volume {}",resourceId);
            }
        } else {
            LOG.info("Volume with id {} does not exist ",resourceId);
        }

    }


    private Volume createVolume(int size, int iiops, VolumeType type) {
        String availabilityZone = getInstance(configurationId).getPlacement()
                .getAvailabilityZone();

        CreateVolumeRequest createVolumeRequest = new CreateVolumeRequest()
                .withSize(size).withVolumeType(type)
                .withAvailabilityZone(availabilityZone);
        if (iiops > 0) {
            createVolumeRequest = createVolumeRequest.withIops(iiops);
        }
        Volume result = ec2client.createVolume(createVolumeRequest).getVolume();
        return result;
    }

    @Override
    public Volume createVolume(int size, VolumeType type) {
        return createVolume(size, 0, type);
    }

    @Override
    public Volume createIO1Volume(int size, int iopsPerGb) {
        // io1 volume size can not be less than 4 Gb
        size = size < MIN_SIZE_OF_OI1_VOLUME ? MIN_SIZE_OF_OI1_VOLUME : size;
        return createVolume(size < MIN_SIZE_OF_OI1_VOLUME ? MIN_SIZE_OF_OI1_VOLUME : size, getIops(iopsPerGb, size), VolumeType.Io1);
    }

    @Override
    public Snapshot createSnapshot(Volume volume) {
        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy.MM.dd'_T'hh:mm:ss");

        String volumeId = volume.getVolumeId();
        LOG.info(format("Starting creating snapshot for %s", volumeId));
        CreateSnapshotRequest snapshotRequest = new CreateSnapshotRequest(
                volumeId,
                volumeId
                        + "__"
                        + formatter.format(new Date(System.currentTimeMillis())));
        CreateSnapshotResult crSnapshotResult = ec2client
                .createSnapshot(snapshotRequest);
        Snapshot snapshot = crSnapshotResult.getSnapshot();
        return snapshot;
    }

    @Override
    public void deleteSnapshot(String snapshotId) {
        LOG.info(format("Deleting snapshot: %s", snapshotId));
        DeleteSnapshotRequest deleteSnapshotRequest = new DeleteSnapshotRequest();
        deleteSnapshotRequest.setSnapshotId(snapshotId);
        try {
            ec2client.deleteSnapshot(deleteSnapshotRequest);
        } catch (Throwable e) {
            LOG.info("Snapshot with id {} does not exist ", snapshotId);
        }
    }

    @Override
    public void cleanupSnapshots(String volumeId, String snapshotIdToLeave) {
        deleteSnapshot(snapshotIdToLeave);
    }

    @Override
    public Snapshot waitForCompleteState(Snapshot snapshot) {
        String state="";
	String progress="";
        Snapshot result;
        do {
            try {
                TimeUnit.SECONDS.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
	
	DescribeSnapshotsResult describeSnapRes 
        = ec2client.describeSnapshots(new DescribeSnapshotsRequest().withSnapshotIds(snapshot.getSnapshotId()));
        state = describeSnapRes.getSnapshots().get(0).getState();	
        progress = describeSnapRes.getSnapshots().get(0).getProgress();
	result = describeSnapRes.getSnapshots().get(0);
	System.out.println("Snapshot status is"+ state + "progress:"+progress);
            //result = syncSnapshot(snapshot);
            //state = result.getState();
            if (state.equals(SnapshotState.Error)) {
                // TODO:exception
            }
        } while (state.equals(SnapshotState.Pending)|| (!progress.equals("100%")) );
        
        return result;
    }

    @Override
    public Snapshot syncSnapshot(Snapshot snapshot) {
        DescribeSnapshotsRequest describeSnapshotsRequest = new DescribeSnapshotsRequest();
        LinkedList<String> ids = new LinkedList<String>();
        ids.add(snapshot.getSnapshotId());
        describeSnapshotsRequest.setSnapshotIds(ids);
        DescribeSnapshotsResult describeSnapshotsResult = ec2client
                .describeSnapshots(describeSnapshotsRequest);
        return describeSnapshotsResult.getSnapshots().get(0);
    }

    @Override
    public Volume waitForAvailableState(Volume volume) {
        String state;
        Volume result;
        do {
            try {
                TimeUnit.SECONDS.sleep(25);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            result = syncVolume(volume);
            state = result.getState();
            System.out.println("waitForAvailableState.current state: " + state);
            if (state.equals(VolumeState.Error.toString())) {
                throw new RuntimeException("error...");
            }
        } while (!state.equals(VolumeState.Available.toString())
                && !state.equals(VolumeState.Deleted.toString()));
        return result;
    }

    @Override
    public Volume getVolume(String volumeId) {
        DescribeVolumesRequest describeVolumesRequest = new DescribeVolumesRequest();
        LinkedList<String> ids = new LinkedList<>();
        ids.add(volumeId);
        describeVolumesRequest.setVolumeIds(ids);
        DescribeVolumesResult describeVolumesResult = ec2client
                .describeVolumes(describeVolumesRequest);
        return describeVolumesResult.getVolumes().get(0);
    }

    @Override
    public Instance getInstance(String instanceId) {
        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest()
                .withInstanceIds(instanceId);
        DescribeInstancesResult describeInstancesResult = ec2client
                .describeInstances(describeInstancesRequest);
        List<Reservation> reservations = describeInstancesResult
                .getReservations();
        for (Reservation res : reservations) {
            if (res.getInstances().size() > 0) {
                return res.getInstances().get(0);
            }
        }
        return null;
    }

    @Override
    public void detachVolume(Volume volume) {
        boolean incorrectState = true;
        long timeout = 10L;
        while (incorrectState) {
            try {
                incorrectState = false;
                ec2client.detachVolume(new DetachVolumeRequest(volume.getVolumeId()));
            } catch (AmazonClientException incorrectStateException) {
                LOG.info(incorrectStateException.getMessage() + "\n Waiting for new try");
                incorrectState = true;
                timeout += timeout < 120 ? timeout * 2 : 0;
                try {
                    TimeUnit.SECONDS.sleep(timeout);
                } catch (InterruptedException e) {
                }
            }
        }
        LOG.info(format("Volume %s unattached", volume.getVolumeId()));
    }

    public Volume createVolumeFromSnapshot(String snapshotId, String availabilityZoneName, VolumeType type, int iopsPerGb) {
        CreateVolumeRequest crVolumeRequest = new CreateVolumeRequest(snapshotId, availabilityZoneName);
        crVolumeRequest.setVolumeType(type);

        if (type.equals(VolumeType.Io1)) {
            Snapshot snapshot = getSnapshot(snapshotId);
            // io1 volume size can not be less than 4 Gb
            int size = snapshot.getVolumeSize() < MIN_SIZE_OF_OI1_VOLUME ? MIN_SIZE_OF_OI1_VOLUME : snapshot.getVolumeSize();
            crVolumeRequest.setSize(size);
            // setting iops
            if (iopsPerGb != 0) {
                crVolumeRequest.setIops(getIops(iopsPerGb,  size));
            }
        }
        return ec2client.createVolume(crVolumeRequest).getVolume();
    }

    @Override
    public Volume syncVolume(Volume volume) {
        DescribeVolumesRequest describeVolumesRequest = new DescribeVolumesRequest().withVolumeIds(volume.getVolumeId());
        DescribeVolumesResult result = ec2client.describeVolumes(describeVolumesRequest);
        return result.getVolumes().get(0);
    }

    @Override
    public void deleteVolume(Volume volume) {
        DeleteVolumeRequest deleteVolumeRequest = new DeleteVolumeRequest(volume.getVolumeId());
        ec2client.deleteVolume(deleteVolumeRequest);
        LOG.info(format("Volume %s deleted", volume.getVolumeId()));
    }

    @Override
    public void attachVolume(Instance instance, Volume volume) {
        String deviceName = getNextAvailableDeviceName(instance);
        AttachVolumeRequest attachVolumeRequest = new AttachVolumeRequest(volume.getVolumeId(),
                instance.getInstanceId(), deviceName);
        AttachVolumeResult res = ec2client.attachVolume(attachVolumeRequest);
        LOG.info(format("\nVolume attached. check instance data\n %s", instance.toString()));

    }

    private String getNextAvailableDeviceName(Instance instance) {

        List<InstanceBlockDeviceMapping> devList = instance
                .getBlockDeviceMappings();
        char lastChar = 'a';
        for (InstanceBlockDeviceMapping map : devList) {
            char ch = map.getDeviceName().charAt(map.getDeviceName().length() - 1);
            if (ch > lastChar) {
                lastChar = ch;
            }
        }
        if (lastChar < 'p' && lastChar >= 'f') {
            lastChar++;
            return "/dev/sd" + (char) lastChar;
        }
        return "/dev/sdf";
    }


    @Override
    public void setResourceName(String resourceId, String value) {
        addTag(resourceId, "Name", value);
    }

    @Override
    public void addTag(String resourceId, String name, String value) {
        CreateTagsRequest r = new CreateTagsRequest().withResources(resourceId)
                .withTags(new Tag().withKey(name).withValue(value));
        ec2client.createTags(r);
    }

    @Override
    public Snapshot getSnapshot(String snapshotId) {
        DescribeSnapshotsResult describeSnapshotsResult = ec2client.describeSnapshots();
        List<Snapshot> snapshots = describeSnapshotsResult.getSnapshots();
        for(Snapshot snapshot: snapshots){
            if(snapshot.getSnapshotId().equals(snapshotId)){
                return snapshot;
            }
        }
        return null;
    }


    int getRetryRestoreAttempts() {
        return retryRestoreAttempts;
    }

    void setRetryRestoreAttempts(int retryRestoreAttempts) {
        this.retryRestoreAttempts = retryRestoreAttempts;
    }

    void setRetryRestoreTimeout(int retryRestoreTimeout) {
        this.retryRestoreTimeout = retryRestoreTimeout;
    }

    // iops can not be less than 100 and more than 20 000
    private int getIops(int iopsPerGb, int volumeSize) {
        int iops = volumeSize * iopsPerGb;
        if (iops < MIN_IOPS_VALUE) {
            return MIN_IOPS_VALUE;
        }
        if (iops > MAX_IOPS_VALUE) {
            return MAX_IOPS_VALUE;
        }
        return iops;
    }
}

