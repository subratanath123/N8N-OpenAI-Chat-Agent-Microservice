package net.ai.chatbot.controller;


import net.ai.chatbot.dao.ProjectDao;
import net.ai.chatbot.dto.Project;
import net.ai.chatbot.dto.ProjectOverview;
import net.ai.chatbot.dto.ProjectTrainingInfo;
import net.ai.chatbot.service.training.ChatBotTrainingService;
import net.ai.chatbot.utils.AuthUtils;
import org.apache.tika.exception.TikaException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectDao projectDao;
    private final ChatBotTrainingService chatBotTrainingService;

    public ProjectController(ProjectDao projectDao, ChatBotTrainingService chatBotTrainingService) {
        this.projectDao = projectDao;
        this.chatBotTrainingService = chatBotTrainingService;
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectOverview> showProjectTrainingInfos(@PathVariable String projectId) {
        List<ProjectTrainingInfo> projectTrainingInfos = projectDao.findProjectTrainingInfoByProjectId(projectId);
        Project project = projectDao.findById(projectId);

        return ResponseEntity.ok(new ProjectOverview(project, projectTrainingInfos));
    }

    @GetMapping("/list")
    public ResponseEntity<List<Project>> showProjectList() {
        List<Project> projects = projectDao.findProjectsByUser(AuthUtils.getEmail());
        return ResponseEntity.ok(projects);
    }

    @PostMapping("/create")
    public ResponseEntity<Project> createNewProject(@ModelAttribute Project request) throws TikaException, IOException {
        Project project = Project.builder()
                .projectName(request.getProjectName())
                .description(request.getDescription())
                .embedWebsiteUrl(request.getEmbedWebsiteUrl())
                .createdBy(AuthUtils.getEmail())
                .createdAt(new Date())
                .chatBotName(request.getChatBotName())
                .chatBotImageUrl(request.getChatBotImageUrl())
                .websiteToTrain(request.getWebsiteToTrain())
                .build();

        Project saved = projectDao.saveProjectIfNotExists(project);

        if (saved == null) {
            return ResponseEntity.unprocessableEntity().build();
        }

        initiateTrainingChatBot(saved);

        return ResponseEntity.ok(saved);
    }

    private void initiateTrainingChatBot(Project project) throws TikaException, IOException {
        String projectName = project.getProjectName();
        String webSite = project.getWebsiteToTrain();
        String description = project.getDescription();
        MultipartFile file = project.getFile();

        if (webSite != null && webSite.length() > 0) {
            chatBotTrainingService.handleWebsiteUrlTraining(project.getId(), webSite, projectName);

        } else if (description != null && description.length() > 0) {
            chatBotTrainingService.handleTextBasedTraining(description, projectName);

        } else if (file != null && !file.isEmpty()) {
            chatBotTrainingService.handleFileTraining(file, projectName);
        }
    }
}

