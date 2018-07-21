/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import java.io.IOException;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
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
    private final String fileName;
    private static final String CHOICE_OF_SHELL = "/bin/bash";
    
    @DataBoundConstructor
    public SlackUploader(String channel, String token, String filePath, String fileName) {
        super();
        this.channel = channel;
        this.token = token;
        this.filePath = filePath;
        this.fileName = fileName;
    }

    public String getChannel() {
        return channel;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return fileName;
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
        LogOutput log = new LogOutput();
        Runtime runtime = Runtime.getRuntime();
        Process process = null;

        try {
            String script = generateScript();
            
            process = runScript(runtime, script);
            
            log.logOutput(listener, process);
        } catch (Throwable cause) {
            log.logOutput(listener, process);
        }
        return true;
    }

    private Process runScript(Runtime runtime, String script) throws IOException {
        Process process = runtime.exec(new String[]{CHOICE_OF_SHELL, "-c", script});
        return process;
    }

    

    private String generateScript() {
        String loop = "for file in $(ls " + filePath + ");";
        loop+="do ";
        String curlRequest = loop + "curl -F file=@$file -F filename=" + fileName
                +" -F channels=" + channel + " -F token=" + token + " https://slack.com/api/files.upload ;";
        String loopDone = curlRequest + "done;";
        return loopDone;
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
        
        public FormValidation doCheckFilePath(@QueryParameter String filePath, @QueryParameter String fileName) {
            if (fileName.length() != 0 && filePath.contains("*")) {
                return FormValidation.error("Do not use regex if you have set a filename");
            } else if (filePath.length() == 0) {
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
            String fileName = req.getParameter("fileName");
            return new SlackUploader(channel, token, filePath, fileName);
        }

        
    }
}
