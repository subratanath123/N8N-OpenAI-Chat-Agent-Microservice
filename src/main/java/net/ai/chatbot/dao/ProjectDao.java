package net.ai.chatbot.dao;

import net.ai.chatbot.dto.Project;
import net.ai.chatbot.dto.ProjectTrainingInfo;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class ProjectDao {

    private final MongoTemplate mongoTemplate;

    public ProjectDao(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String fetchProjectIdByName(String projectName, String createdBy) {
        Query query = new Query()
                .addCriteria(Criteria.where("projectName").is(projectName)
                        .and("createdBy").is(createdBy));
        query.fields().include("id");

        return Optional.ofNullable(mongoTemplate.findOne(query, Project.class))
                .map(Project::getId)
                .orElse(null);
    }


    public List<ProjectTrainingInfo> findProjectTrainingInfoByProjectId(String projectId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("projectId").is(projectId));

        return mongoTemplate.find(query, ProjectTrainingInfo.class);
    }

    public Project saveProjectIfNotExists(Project project) {
        if (fetchProjectIdByName(project.getProjectName(), project.getCreatedBy()) == null) {
            project.setCreatedAt(new Date());
            return mongoTemplate.save(project);
        }

        return null;
    }

    public List<Project> findProjectsByUser(String userEmail) {
        Query query = new Query(Criteria.where("createdBy").is(userEmail));
        return mongoTemplate.find(query, Project.class);
    }

    public List<Project> findAllProjects() {
        return mongoTemplate.findAll(Project.class);
    }

    public Project findById(String projectId) {
        return mongoTemplate.findById(projectId, Project.class);
    }

}
