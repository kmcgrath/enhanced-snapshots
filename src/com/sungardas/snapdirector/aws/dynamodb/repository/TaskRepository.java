package com.sungardas.snapdirector.aws.dynamodb.repository;

import java.util.List;

import com.sungardas.snapdirector.aws.dynamodb.model.TaskEntry;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.springframework.data.repository.CrudRepository;

@EnableScan
public interface TaskRepository extends CrudRepository<TaskEntry, String> {
	List<TaskEntry> findByStatusAndInstanceId(String status, String instanceId);
}
