package org.github.irengrig.fossil4idea.commandLine;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.ProcessEventListener;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.github.irengrig.fossil4idea.FossilVcs;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/13/13
 * Time: 8:34 PM
 */
public class FossilSimpleCommand extends FossilTextCommand {
  private static final Logger LOG = Logger.getInstance("#FossilSimpleCommand");
  private final StringBuilder myStderr;
  private final StringBuilder myStdout;
  private final Set<String> myStartBreakSequence;

  public FossilSimpleCommand(Project project, File workingDirectory, @NotNull FCommandName commandName) {
    this(project, workingDirectory, commandName, null);
  }

  public FossilSimpleCommand(Project project, File workingDirectory, @NotNull FCommandName commandName,
                             final String breakSequence) {
    super(project, workingDirectory, commandName);

    myStderr = new StringBuilder();
    myStdout = new StringBuilder();
    myStartBreakSequence = new HashSet<String>();
    if (breakSequence != null) {
      myStartBreakSequence.add(breakSequence);
    }
    addUsualSequences();
  }

  private void addUsualSequences() {
    myStartBreakSequence.add("If you have recently updated your fossil executable, you might\n" +
            "need to run \"fossil all rebuild\" to bring the repository\n" +
            "schemas up to date.");
    myStartBreakSequence.add("database is locked");
  }

  public void addBreakSequence(final String s) {
    myStartBreakSequence.add(s);
  }

  @Override
  protected void processTerminated(int exitCode) {
    //
  }

  @Override
  protected void onTextAvailable(String text, Key outputType) {
    if (ProcessOutputTypes.STDOUT.equals(outputType)) {
      if (myStdout.length() == 0 && isInBreakSequence(text)) {
        myStdout.append(text);
        destroyProcess();
        return;
      }
      myStdout.append(text);
    } else if (ProcessOutputTypes.STDERR.equals(outputType)) {
      if (myStderr.length() == 0 && isInBreakSequence(text)) {
        myStderr.append(text);
        destroyProcess();
        return;
      }
      myStderr.append(text);
    }
  }

  private boolean isInBreakSequence(final String text) {
    for (String s : myStartBreakSequence) {
      if (text.contains(s)) return true;
    }
    return false;
  }

  public StringBuilder getStderr() {
    return myStderr;
  }

  public StringBuilder getStdout() {
    return myStdout;
  }

  public String run() throws VcsException {
    final VcsException[] ex = new VcsException[1];
    final String[] result = new String[1];
    addListener(new ProcessEventListener() {
      @Override
      public void processTerminated(int exitCode) {
        try {
          if (exitCode == 0) {
            result[0] = getStdout().toString();
            LOG.info(myCommandLine.getCommandLineString() + " >>\n" + result[0]);
            System.out.println(myCommandLine.getCommandLineString() + " >>\n" + result[0]);
          }
          else {
            String msg = getStderr().toString();
            if (msg.length() == 0) {
              msg = getStdout().toString();
            }
            if (msg.length() == 0) {
              msg = "Fossil process exited with error code: " + exitCode;
            }
            LOG.info(myCommandLine.getCommandLineString() + " >>\n" + msg);
            System.out.println(myCommandLine.getCommandLineString() + " >>\n" + msg);
            ex[0] = new VcsException(msg);
          }
        }
        catch (Throwable t) {
          ex[0] = new VcsException(t.toString(), t);
        }
      }

      @Override
      public void startFailed(Throwable exception) {
        ex[0] = new VcsException("Process failed to start (" + myCommandLine.getCommandLineString() + "): " + exception.toString(), exception);
      }
    });
    start();
    if (myProcess != null) {
      waitFor();
    }
    if (ex[0] != null) {
      FossilVcs.getInstance(myProject).checkVersion();
      throw ex[0];
    }
    if (result[0] == null) {
      throw new VcsException("Svn command returned null: " + myCommandLine.getCommandLineString());
    }
    return result[0];
  }
}
