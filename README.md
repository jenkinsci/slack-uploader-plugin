# SlackUploader

Started as a necessity to upload android apk to slack.

## Instructions

1. Create a [Slack account](https://www.slack.com) and obtain your token.  To obtain a token go to `https://api.slack.com/custom-integrations/legacy-tokens`,  and then create on `create token`
2. Install the plugin on Jenkins server. Go to `Manage Jenkins -> Manage Plugins`  and select `Slack Uploader` from the list. 
3. Configure your Jenkins job as a **Post-Build action**.
4. Enter the channel and the file path of the files you want to upload. Note that you should provide a full path to the like `/var/jenkins_home/workspace/Android/YourJobName/app/build/outputs/apk/release/app-release.apk`
5. Start a build and watch as the magic happens.
