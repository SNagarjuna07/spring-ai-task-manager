package com.nagarjuna.toolcalling.service;

import com.nagarjuna.toolcalling.entity.Task;
import com.nagarjuna.toolcalling.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskToolService {

    private final TaskRepository taskRepository;

    // Create a new task
    @Tool(description = "Create a new task with a given title")
    public String createTask(
            @ToolParam(description = "task title")
            String title
    ) {

        log.info("Fetched new task creation tool");

        Task task = new Task();

        task.setTitle(title);
        task.setDone(false);

        taskRepository.save(task);

        return "Created task '" + title + "' with ID " + task.getId();
    }

    // List all pending tasks
    @Tool(description = "List all pending tasks")
    public String incompleteTasks() {

        log.info("Fetched listing all pending tasks tool");

        List<Task> incomplete = taskRepository.findByDoneFalse();

        return incomplete
                .stream()
                .map(t ->
                        t.getId() + ": " + t.getTitle())
                .collect(
                        Collectors.joining("\n")
                );
    }

    // Search by keyword
    @Tool(description = "Search all the tasks by keyword")
    public String searchByKeyword(
            @ToolParam(description = "The keyword to perform search")
            String keyword
    ) {

        log.info("Fetched searching a task tool");

        log.info("Searching for keyword {}", keyword);

        List<Task> matching = taskRepository.findByTitleContainingIgnoreCase(keyword);

        if (matching.isEmpty()) {
            return "No tasks found matching: " + keyword;
        }

        return matching
                .stream()
                .map(t ->
                        t.getId() + ": " + t.getTitle()
                )
                .collect(
                        Collectors.joining("\n")
                );

    }

    // Mark task as completed
    @Tool(description = "Mark task as completed by matching its title (case-insensitive)")
    public String completeTask(
            @ToolParam(description = "Task's title (case-insensitive)")
            String title
    ) {

        log.info("Fetched mark the task as COMPLETED tool");

        return taskRepository.findByTitleIgnoreCaseAndDoneFalse(title)
                .map(task -> {

                    if (task.isDone()) {
                        return "Task '" + title + "' is already completed.";
                    }

                    task.setDone(true);
                    taskRepository.save(task);

                    return "Marked task '" + title + "' as completed.";
                })
                .orElse("Task '" + title + "' not found.");
    }

    // Deleting completed tasks
    @Tool(description = "Propose deletion of all completed tasks. Do not delete - requires seperate confirmation")
    public String proposeDeleteCompleted() {

        log.info("Fetched deleting completed tasks tool");

        List<Task> done = taskRepository.findByDoneTrue();

        if (done.isEmpty()) {
            return "No completed tasks to delete";
        }

        String titles = done
                .stream()
                .map(Task::getTitle)
                .collect(
                        Collectors.joining(
                                ", "
                        ));

        return "Proposal DELETE " + done.size() + " completed tasks: [" + titles + "]. " +
                "Ask user to confirm it via /api/v1/tasks/confirm-delete endpoint.";
    }
}