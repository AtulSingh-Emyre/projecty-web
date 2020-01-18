package com.projecty.projectyweb.project.role;

import com.projecty.projectyweb.misc.RedirectMessage;
import com.projecty.projectyweb.misc.RedirectMessageTypes;
import com.projecty.projectyweb.notification.NotificationService;
import com.projecty.projectyweb.notification.Notifications;
import com.projecty.projectyweb.project.Project;
import com.projecty.projectyweb.project.ProjectRepository;
import com.projecty.projectyweb.user.User;
import com.projecty.projectyweb.user.UserService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProjectRoleService {
    private final ProjectRoleRepository projectRoleRepository;
    private final UserService userService;
    private final ProjectRepository projectRepository;
    private final MessageSource messageSource;
    private final NotificationService notificationService;

    public ProjectRoleService(ProjectRoleRepository projectRoleRepository, UserService userService, ProjectRepository projectRepository, MessageSource messageSource, NotificationService notificationService) {
        this.projectRoleRepository = projectRoleRepository;
        this.userService = userService;
        this.projectRepository = projectRepository;
        this.messageSource = messageSource;
        this.notificationService = notificationService;
    }

    public void save(ProjectRole projectRole) {
        projectRoleRepository.save(projectRole);
    }

    //FIXME Error while adding project with admin in same tiem
    public List<ProjectRole> addRolesToProjectByUsernames(Project project, List<String> usernames, List<RedirectMessage> messages) {
        if (project.getId() == null)
            project = projectRepository.save(project);
        List<ProjectRole> projectRoles = new ArrayList<>();
        if (usernames != null) {
            Set<User> users = userService.getUserSetByUsernamesWithoutCurrentUser(usernames);
            removeExistingUsersInProjectFromSet(users, project);
            for (User user : users
            ) {
                ProjectRole projectRole = new ProjectRole(ProjectRoles.USER, user, project);
                projectRoles.add(projectRole);
                RedirectMessage message = new RedirectMessage();
                message.setType(RedirectMessageTypes.SUCCESS);
                String text = messageSource.getMessage(
                        "projectRole.add.success",
                        new Object[]{user.getUsername(), project.getName()},
                        LocaleContextHolder.getLocale());
                notificationService.createNotificationAndSave(
                        user, Notifications.USER_ADDED_TO_PROJECT, new Long[]{project.getId()});
                message.setText(text);
                messages.add(message);
            }
        }
        List<ProjectRole> savedProjectRoles = new ArrayList<>();
        projectRoles.forEach(projectRole -> savedProjectRoles.add(projectRoleRepository.save(projectRole)));
        if (project.getProjectRoles() == null) {
            project.setProjectRoles(savedProjectRoles);
        } else if (projectRoles.size() > 0) {
            project.getProjectRoles().addAll(savedProjectRoles);
        }
        projectRepository.save(project);
        return savedProjectRoles;
    }

    private void removeExistingUsersInProjectFromSet(Set<User> users, Project project) {
        if (project.getId() != null) {
            Set<User> existingUsers = getProjectRoleUsers(project);
            users.removeAll(existingUsers);
        }
    }

    public Set<User> getProjectRoleUsers(Project project) {
        List<ProjectRole> projectRoles = projectRoleRepository.findByProjectOrderByIdAsc(project);
        Set<User> users = new HashSet<>();
        projectRoles.forEach(projectRole -> users.add(projectRole.getUser()));
        return users;
    }

    public void addCurrentUserToProjectAsAdmin(Project project) {
        User current = userService.getCurrentUser();
        ProjectRole projectRole = new ProjectRole(ProjectRoles.ADMIN, current, project);
        if (project.getProjectRoles() == null) {
            List<ProjectRole> projectRoles = new ArrayList<>();
            projectRoles.add(projectRole);
            project.setProjectRoles(projectRoles);
        } else {
            project.getProjectRoles().add(projectRole);
        }
    }

    public void deleteRoleFromProject(ProjectRole role) {
        Project project = role.getProject();
        List<ProjectRole> projectRoles = project.getProjectRoles();
        projectRoles.remove(role);
        project.setProjectRoles(projectRoles);
        projectRepository.save(project);
    }

    public void leaveProject(Project project, User user) throws NoAdminsInProjectException {
        Optional<ProjectRole> optionalProjectRole = projectRoleRepository.findRoleByUserAndProject(user, project);
        if (optionalProjectRole.isPresent()) {
            ProjectRole projectRole = optionalProjectRole.get();
            int admins = projectRoleRepository.countByProjectAndName(project, ProjectRoles.ADMIN);
            if ((projectRole.getName().equals(ProjectRoles.ADMIN) && admins - 1 > 0) || project.getName().equals(ProjectRoles.USER)) {
                project.getProjectRoles().remove(optionalProjectRole.get());
                projectRepository.save(project);
            } else {
                throw new NoAdminsInProjectException();
            }
        }
    }
}
