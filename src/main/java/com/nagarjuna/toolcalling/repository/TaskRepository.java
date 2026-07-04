package com.nagarjuna.toolcalling.repository;

import com.nagarjuna.toolcalling.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByDoneTrue();

    List<Task> findByDoneFalse();

    Optional<Task> findByTitleIgnoreCaseAndDoneFalse(String title);

    List<Task> findByTitleContainingIgnoreCase(String keyword);
}
