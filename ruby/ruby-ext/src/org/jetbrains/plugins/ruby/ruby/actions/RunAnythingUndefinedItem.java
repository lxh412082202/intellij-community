package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import icons.RubyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.ruby.RModuleUtil;
import org.jetbrains.plugins.ruby.ruby.run.RubyAbstractRunner;
import org.jetbrains.plugins.ruby.rvm.RVMSupportUtil;
import org.jetbrains.plugins.ruby.version.management.rbenv.gemsets.RbenvGemsetManager;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class RunAnythingUndefinedItem extends RunAnythingItem {
  static final Icon UNDEFINED_COMMAND_ICON = RubyIcons.RunAnything.Run_anything;
  private static final ArrayList<String> COMMANDS = ContainerUtil.newArrayList(
    "bundle", "rake", "erb", "gem", "irb", "rdoc", "ruby", "rails");
  @Nullable private final Module myModule;
  @NotNull private final String myCommandLine;
  @NotNull private final Project myProject;

  public RunAnythingUndefinedItem(@NotNull Project project, @Nullable Module module, @NotNull String commandLine) {
    myProject = project;
    myModule = module;
    myCommandLine = commandLine;
  }

  @Override
  public void run(@NotNull Executor executor, @Nullable VirtualFile workDirectory) {
    Collection<String> commands = RunAnythingCache.getInstance(myProject).getState().undefinedCommands;
    commands.remove(myCommandLine);
    commands.add(myCommandLine);

    Sdk sdk = RModuleUtil.getInstance().findRubySdkForModule(myModule);

    String command = myCommandLine;
    Map<String, String> env = ContainerUtil.newHashMap();
    if (RVMSupportUtil.isRVMInterpreter(sdk)) {
      command = getRVMAwareCommand(sdk);
    }
    else if (RbenvGemsetManager.isRbenvSdk(sdk)) {
      command = getRbenvAwareCommand(sdk, env);
    }

    GeneralCommandLine commandLine = new GeneralCommandLine(ParametersListUtil.parse(command))
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withEnvironment(env)
      .withWorkDirectory(RunAnythingItem.getActualWorkDirectory(myProject, workDirectory));

    runInConsole(commandLine);
  }

  private void runInConsole(@NotNull GeneralCommandLine commandLine) {
    try {
      KillableColoredProcessHandler processHandler = new KillableColoredProcessHandler(commandLine) {
        @Override
        protected void notifyProcessTerminated(int exitCode) {
          RunContentDescriptor contentDescriptor
            = ExecutionManager.getInstance(myProject).getContentManager()
                              .findContentDescriptor(DefaultRunExecutor.getRunExecutorInstance(), this);

          if (contentDescriptor != null && contentDescriptor.getExecutionConsole() instanceof ConsoleView) {
            ((ConsoleView)contentDescriptor.getExecutionConsole())
              .print(RBundle.message("run.anything.console.process.finished", exitCode), ConsoleViewContentType.SYSTEM_OUTPUT);
          }
          super.notifyProcessTerminated(exitCode);
        }
      };

      final RunContentExecutor contentExecutor = new RunContentExecutor(myProject, processHandler)
        .withTitle(RBundle.message("run.anything.console.title"))
        .withStop(processHandler::destroyProcess, () -> !processHandler.isProcessTerminated())
        .withActivateToolWindow(true);

      ApplicationManager.getApplication().invokeLater(() -> {
        if (!myProject.isDisposed()) {
          contentExecutor.run();
        }
      });
    }
    catch (ExecutionException e) {
      Messages.showInfoMessage(myProject, e.getMessage(), RBundle.message("run.anything.console.error.title"));
    }
  }

  private String getRbenvAwareCommand(@NotNull Sdk sdk, @NotNull Map<String, String> env) {
    String exeCommand = myCommandLine.contains(" ") ? StringUtil.substringBefore(myCommandLine, " ") : myCommandLine;
    String shimsExec = RbenvGemsetManager.getShimsCommandPath(Objects.requireNonNull(exeCommand));
    if (shimsExec == null) return myCommandLine;

    for (String shim : Objects.requireNonNull(new File(shimsExec).getParentFile().list())) {
      if (shim.equals(exeCommand)) {
        shimsExec = RbenvGemsetManager.getShimsCommandPath(shim);
        break;
      }
    }

    if (shimsExec == null || !RunAnythingCache.getInstance(myProject).CAN_RUN_RBENV) return myCommandLine;

    RubyAbstractRunner.patchRbenvEnv(env, myModule, sdk);

    return shimsExec + (myCommandLine.contains(" ") ? " " + StringUtil.substringAfter(myCommandLine, " ") : "");
  }

  @NotNull
  private String getRVMAwareCommand(@NotNull Sdk sdk) {
    String exeCommand = (myCommandLine.contains(" ")) ? StringUtil.substringBefore(myCommandLine, " ") : myCommandLine;
    String version = RVMSupportUtil.getRVMSdkVersion(sdk);

    if (!COMMANDS.contains(exeCommand) || !RunAnythingCache.getInstance(myProject).CAN_RUN_RVM || version == null) return myCommandLine;

    return "rvm " + version + " do " + myCommandLine;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RunAnythingUndefinedItem item = (RunAnythingUndefinedItem)o;
    return Objects.equals(myModule, item.myModule) &&
           Objects.equals(myCommandLine, item.myCommandLine) &&
           Objects.equals(myProject, item.myProject);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myModule, myCommandLine, myProject);
  }

  @Override
  public String getText() {
    return myCommandLine;
  }

  @Override
  public Icon getIcon() {
    return UNDEFINED_COMMAND_ICON;
  }
}
