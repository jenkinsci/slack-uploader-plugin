/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.remoting.Callable;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import hudson.util.FormValidation;
import jenkins.security.MasterToSlaveCallable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author Shitij
 */
public class SlackUploader extends Recorder {
   
    private final String channel;
    private final String token;
    private final String filePath;
    private static final String CHOICE_OF_SHELL = "/bin/bash";

    @DataBoundConstructor
    public SlackUploader(String channel, String token, String filePath) {
        super();
        this.channel = channel;
        this.token = token;
        this.filePath = filePath;
    }

    public String getChannel() {
        return channel;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getToken() {
        return token;
    }


    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        //To change body of generated methods, choose Tools | Templates.
        Runtime runtime = Runtime.getRuntime();
        Process process = null;

        LogOutput log = new LogOutput();

        try {
            generateScript(build, launcher, listener);
        } catch (Throwable cause) {
            throw new RuntimeException(cause);
        }
        return true;
    }

    private Process runScript(Runtime runtime, String script) throws IOException {
        Process process = runtime.exec(new String[]{CHOICE_OF_SHELL, "-c", script});
        return process;
    }

    private void generateScript(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws Throwable {
        String mask = TokenMacro.expandAll(build, listener, filePath);

        listener.getLogger().println(String.format("Invoking Uploader (channel=%s, filePath=%s)", channel, filePath));

        launcher.getChannel().callAsync(new FileUploadRunner(listener, mask, channel, token)).get();
    }

    @Override
    public BuildStepDescriptor getDescriptor() {
        SlackUploaderDescriptor slackBuilderDescriptor = (SlackUploaderDescriptor)super.getDescriptor(); //To change body of generated methods, choose Tools | Templates.
        return slackBuilderDescriptor;
    }
    
    
    @Extension
    public static final class SlackUploaderDescriptor extends BuildStepDescriptor<Publisher> {
        
        public SlackUploaderDescriptor(){
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return FreeStyleProject.class.isAssignableFrom(jobType);
        }

        @Override
        public String getDisplayName() {
            return "Post files to Slack";
        }

        @Override
        public void doHelp(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            super.doHelp(req, rsp); //To change body of generated methods, choose Tools | Templates.
        }
        
        public FormValidation doCheckChannel(@QueryParameter String channel) {
            if (channel.length() == 0) {
                return FormValidation.error("Cannot be empty");
            }
            for (int i = 0; i< channel.length(); i++) {
                if (channel.charAt(i) == ',' && channel.charAt(i+1) !='#') {
                    return FormValidation.error("Channels should be specified wihtout anything between comma. eg - #ch1,#ch2,#ch3");
                }
            }
            return FormValidation.ok();
        }
        
        public FormValidation doCheckFilePath(@QueryParameter String filePath) {
            if (filePath.length() == 0) {
                return FormValidation.error("Cannot be empty");
            }
            return FormValidation.ok();
        }
        
        public FormValidation doCheckToken (@QueryParameter String token) {
            if (token.length() == 0) {
                return FormValidation.error("Cannot be empty");
            }
            return FormValidation.ok();
        }

        @Override
        public SlackUploader newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String channel = req.getParameter("channel");
            String token = req.getParameter("token");
            String filePath = req.getParameter("filePath");
            return new SlackUploader(channel, token, filePath);
        }

        
    }

    public static class FileUploadRunner extends MasterToSlaveCallable<Boolean, Throwable> implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String filePath;

        private final String channel;

        private final String token;

        private final BuildListener listener;

        public FileUploadRunner(BuildListener listener, String filePath, String channel, String token) {
            this.listener = listener;
            this.filePath = filePath;
            this.channel = channel;
            this.token = token;
        }

        @Override
        public Boolean call() throws Throwable {
            final List<String> commandList = new ArrayList<>();

            String dirName = filePath;
            String includeMask = "**/*";

            final int i = filePath.indexOf('*');

            if (-1 != i) {
                dirName = filePath.substring(0, i);
                includeMask = filePath.substring(i);
            }

            listener.getLogger().println(String.format("Using dirname=%s and includeMask=%s", dirName, includeMask));

            new DirScanner.Glob(includeMask, null).scan(new File(dirName), new FileVisitor() {
                @Override
                public void visit(File f, String relativePath) throws IOException {
                    if (f.isFile()) {
                        final String command = String.format("curl -F file=@\"%s\" -F channels=\"%s\" -F token=\"%s\" https://slack.com/api/files.upload", f.getAbsolutePath(), channel, token);

                        listener.getLogger().println("Adding file " + f.getAbsolutePath());

                        commandList.add(command);
                    }
                }
            });

            String allCommands = StringUtils.join(commandList, " ; ");

            if (commandList.isEmpty()) {
                listener.getLogger().println("No files found for mask=" + this.filePath);
            }

            Runtime.getRuntime().exec(new String[] { "/bin/sh", "-c", allCommands});

            return ! commandList.isEmpty();
        }
    }
}
