package net.ai.chatbot.dto;

import java.util.List;

public record ProjectOverview(Project project, List<ProjectTrainingInfo> projectTrainingInfoList) {

}
