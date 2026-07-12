package com.executionos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import com.executionos.controller.ResourceControllers;

@EnableJpaAuditing
@Import({
        ResourceControllers.TaskController.class,
        ResourceControllers.ScheduleController.class,
        ResourceControllers.FocusController.class,
        ResourceControllers.NoteController.class,
        ResourceControllers.AttachmentController.class,
        ResourceControllers.KnowledgeController.class,
        ResourceControllers.JournalController.class,
        ResourceControllers.ReadModelController.class,
        ResourceControllers.AdminController.class
})
@SpringBootApplication
public class ExecutionOsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExecutionOsApplication.class, args);
    }
}
