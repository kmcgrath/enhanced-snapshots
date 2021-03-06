package com.sungardas.enhancedsnapshots.aws.dynamodb.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.util.json.Jackson;
import org.json.JSONObject;


@DynamoDBTable(tableName = "Tasks")
public class TaskEntry {

    @DynamoDBHashKey(attributeName = "id")
    private String id;

    @DynamoDBAttribute(attributeName = "priority")
    private int priority;

    @DynamoDBAttribute(attributeName = "worker")
    private String worker;

    @DynamoDBAttribute(attributeName = "status")
    private String status;

    @DynamoDBAttribute(attributeName = "type")
    private String type;

    @DynamoDBAttribute(attributeName = "volume")
    private String volume;

    @DynamoDBAttribute(attributeName = "schedulerManual")
    private String schedulerManual;

    @DynamoDBAttribute(attributeName = "schedulerName")
    private String schedulerName;

    @DynamoDBAttribute(attributeName = "schedulerTime")
    private String schedulerTime;

    @DynamoDBAttribute(attributeName = "instanceId")
    private String instanceId;

    @DynamoDBAttribute(attributeName = "options")
    private String options;

    @DynamoDBAttribute
    private String cron;

    @DynamoDBAttribute
    private String regular = Boolean.FALSE.toString();

    @DynamoDBAttribute
    private String enabled;

    @DynamoDBAttribute
    private String expirationDate;

    public TaskEntry() {
        super();
    }

    @Deprecated
    public TaskEntry(String priority, String status, String type, String volume,
                     String schedulerManual, String schedulerName, String schedulerTime, String instanceId) {
        this(priority, instanceId, status, type, volume, schedulerManual, schedulerName, schedulerTime, instanceId, null);
    }

    @Deprecated
    public TaskEntry(String priority, String worker, String status, String type, String volume,
                     String schedulerManual, String schedulerName, String schedulerTime, String instanceId,
                     String options) {
        this.priority = Integer.parseInt(priority);
        this.worker = worker;
        this.status = status;
        this.type = type;
        this.volume = volume;
        this.schedulerManual = schedulerManual;
        this.schedulerName = schedulerName;
        this.schedulerTime = schedulerTime;
        this.instanceId = instanceId;
        this.options = options;
    }

    public TaskEntry(JSONObject jsonTask) {
        this();

        try {
            this.setPriority(jsonTask.getInt("priority"));
        } catch (RuntimeException emptyPriority) {
            this.setPriority(0);
        }
        setStatus(jsonTask.getString("status"));
        setType(jsonTask.getString("type"));
        setVolume(jsonTask.optString("volume"));
        setSchedulerManual(jsonTask.getBoolean("schedulerManual"));
        setSchedulerName(jsonTask.optString("schedulerName"));
        setSchedulerTime(jsonTask.optString("schedulerTime"));
        setId(jsonTask.getString("id"));
        setWorker(jsonTask.getString("worker"));
        setInstanceId(jsonTask.getString("instanceId"));
        setOptions(jsonTask.optString("options"));
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getWorker() {
        return worker;
    }

    public void setWorker(String worker) {
        this.worker = worker;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getSchedulerManual() {
        return String.valueOf(schedulerManual);
    }

    public void setSchedulerManual(boolean schedulerManual) {
        this.schedulerManual = String.valueOf(schedulerManual);
    }

    public void setSchedulerManual(String schedulerManual) {
        this.schedulerManual = schedulerManual;
    }

    public String getSchedulerName() {
        return schedulerName;
    }

    public void setSchedulerName(String schedulerName) {
        this.schedulerName = schedulerName;
    }

    public String getSchedulerTime() {
        return schedulerTime;
    }

    public void setSchedulerTime(String schedulerTime) {
        this.schedulerTime = schedulerTime;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getRegular() {
        return regular;
    }

    public void setRegular(String regular) {
        this.regular = regular;
    }

    public void setRegular(boolean regular) {
        this.regular = String.valueOf(regular);
    }

    public String getEnabled() {
        return enabled;
    }

    public void setEnabled(String enabled) {
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = String.valueOf(enabled);
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    @DynamoDBIgnore
    public String getSourceFileName() {
        return options.split(", ")[0];
    }

    @DynamoDBIgnore
    public String getAvailabilityZone() {
        return options.split(", ")[1];
    }


    @Deprecated
    @Override
    public String toString() {
        return Jackson.toJsonString(this);
    }

    public enum TaskEntryType {
        BACKUP("backup"),
        RESTORE("restore"),
        DELETE("delete"),
        SYSTEM_BACKUP("system_backup"),
        UNKNOWN("unknown");

        private String type;

        TaskEntryType(String type) {
            this.type = type;
        }

        public static TaskEntryType getType(String type) {
            try {
                return valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }

        public String getType() {
            return type;
        }

    }

    public enum TaskEntryStatus {
        WAITING("waiting"),
        RUNNING("running"),
        QUEUED("queued"),
        COMPLETE("complete"),
        ERROR("error");

        private String status;

        TaskEntryStatus(String status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return status;
        }
        public String getStatus() {
            return status;
        }

    }
}
