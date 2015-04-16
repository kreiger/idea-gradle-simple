package com.linuxgods.kreiger.idea.gradle.simple;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.MessageView;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class GradleBuildFile {
    private final String name;
    private final List<GradleBuildFile.Task> tasks = new ArrayList<>();
    private final File directory;

    public GradleBuildFile(VirtualFile buildFile) {
        directory = new File(buildFile.getParent().getCanonicalPath());
        ProjectConnection projectConnection = getProjectConnection();
        try {
            GradleProject project = projectConnection.getModel(GradleProject.class);
            this.name = project.getName();
            DomainObjectSet<? extends GradleTask> tasks = project.getTasks();
            for (GradleTask task : tasks) {
                this.tasks.add(new Task(task));
            }
        } finally {
            projectConnection.close();
        }

    }

    private ProjectConnection getProjectConnection() {
        GradleConnector gradleConnector = GradleConnector.newConnector();
        return gradleConnector.forProjectDirectory(directory).connect();
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public File getDirectory() {
        return directory;
    }

    class Task {

        private final String name;

        public Task(GradleTask task) {
            this.name = task.getName();
        }

        @Override
        public String toString() {
            return name;
        }

        public GradleBuildFile getBuildFile() {
            return GradleBuildFile.this;
        }

        public String getName() {
            return name;
        }

        public void execute(Project project) {
            MessageView messageView = MessageView.SERVICE.getInstance(project);
            ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
            ContentManager contentManager = messageView.getContentManager();
            Content content = contentManager.getFactory().createContent(console.getComponent(), getBuildFile().getName() + " " + name, true);
            contentManager.addContent(content);
            contentManager.setSelectedContent(content);
            final ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
            if (tw != null) {
                tw.activate(null, false);
            }
            final SimpleProcessHandler processHandler = new SimpleProcessHandler();
            console.attachToProcess(processHandler);
            getProjectConnection().newBuild()
                    .forTasks(name)
                    .setStandardOutput(processHandler.createOutputStream(ProcessOutputTypes.STDOUT))
                    .setStandardError(processHandler.createOutputStream(ProcessOutputTypes.STDERR))
                    .run(new ResultHandler<Void>() {
                        @Override
                        public void onComplete(Void aVoid) {
                            refreshDirectory();
                        }

                        @Override
                        public void onFailure(GradleConnectionException e) {
                            refreshDirectory();
                        }
                    });
        }

        private class SimpleProcessHandler extends ProcessHandler {

            @NotNull
            public OutputStream createOutputStream(final Key key) {
                return new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        notifyTextAvailable("" + (char) b, key);
                    }
                };
            }

            @Override
            protected void destroyProcessImpl() {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void detachProcessImpl() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean detachIsDefault() {
                throw new UnsupportedOperationException();
            }

            @Nullable
            @Override
            public OutputStream getProcessInput() {
                return null;
            }
        }
    }

    private void refreshDirectory() {
        VirtualFile directory = LocalFileSystem.getInstance().findFileByIoFile(getDirectory());
        if (null != directory) {
            directory.refresh(true, true);
        }
    }
}
