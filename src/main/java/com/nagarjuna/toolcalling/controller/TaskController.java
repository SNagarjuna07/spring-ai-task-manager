package com.nagarjuna.toolcalling.controller;

import com.nagarjuna.toolcalling.entity.Task;
import com.nagarjuna.toolcalling.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskRepository taskRepository;

    @DeleteMapping("/confirm-delete")
    public ResponseEntity<String> deleteCompletedTasks() {

        List<Task> done = taskRepository.findByDoneTrue();

        taskRepository.deleteAll(done);

        return ResponseEntity
                .ok(
                        done.size() + " tasks deleted."
                );
    }
}
