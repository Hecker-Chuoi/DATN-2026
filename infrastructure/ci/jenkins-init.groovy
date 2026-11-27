// Cấu hình Jenkins bằng script (tuỳ chọn, thay cho bấm UI ở docs/cicd/cac_buoc/01 §8–12 và 06): idempotent, chạy được nhiều lần.
// Tạo: JDK `jdk-21` (JDK cài sẵn trên máy), credential `app-server-ssh` (khoá /var/lib/jenkins/.ssh/id_deploy, user deployer),
// folder DATN + Multibranch `datn` (nguồn GitHub, credential github-token, quét 1 ngày/lần) và job `smoke-test`.
// KHÔNG tạo github-token / dockerhub (bí mật: tạo tay ở Manage Jenkins > Credentials) và KHÔNG cài webhook GitHub.
//
// Cách dùng (🐧 trên datn-jenkins; Jenkins phải khởi động lại để chạy script init):
//   sed -e 's/__APP_SERVER_IP__/<IP private datn-app>/' -e 's/__GH_OWNER__/<chủ repo>/' -e 's/__GH_REPO__/<tên repo>/' jenkins-init.groovy \
//     | sudo -u jenkins tee /var/lib/jenkins/init.groovy.d/datn-setup.groovy >/dev/null     // trước đó: sudo -u jenkins mkdir -p /var/lib/jenkins/init.groovy.d
//   sudo systemctl restart jenkins ; xem log:  sudo journalctl -u jenkins | grep '[datn]'
//   XONG thì xoá file:  sudo rm /var/lib/jenkins/init.groovy.d/datn-setup.groovy
// Bẫy đã gặp: BranchDiscoveryTrait phải là bản của github_branch_source (có tham số), không phải jenkins.plugins.git.traits;
// nếu script lỗi giữa chừng, project đã createProject() thì rỗng nguồn → script này kiểm tra "chưa có nguồn" chứ không chỉ "chưa có job".
import jenkins.model.*
import hudson.model.*
import hudson.tools.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey
import com.cloudbees.hudson.plugins.folder.Folder
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import jenkins.branch.BranchSource
import org.jenkinsci.plugins.github_branch_source.*
import com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger

def j = Jenkins.get()

// 1) JDK 21 (dùng JDK cài sẵn trên máy, doc 01 §8)
def jdkD = j.getDescriptorByType(JDK.DescriptorImpl)
if (!jdkD.installations.any { it.name == 'jdk-21' }) {
  jdkD.setInstallations((jdkD.installations.toList() + new JDK('jdk-21', '/usr/lib/jvm/java-21-openjdk-amd64')) as JDK[])
  jdkD.save()
  println '[datn] đã thêm JDK jdk-21'
}

// 2) Credential app-server-ssh (khoá id_deploy, user deployer)
def store = SystemCredentialsProvider.getInstance().getStore()
def existing = CredentialsProvider.lookupCredentials(BasicSSHUserPrivateKey, j, null, null).find { it.id == 'app-server-ssh' }
if (!existing) {
  def key = new File('/var/lib/jenkins/.ssh/id_deploy').text
  store.addCredentials(Domain.global(), new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, 'app-server-ssh', 'deployer',
      new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(key), '', 'SSH deployer@datn-app (chỉ chạy được deploy.sh)'))
  println '[datn] đã tạo credential app-server-ssh'
}

// 3) Folder DATN + Multibranch datn
def folder = j.getItem('DATN') ?: j.createProject(Folder, 'DATN')
def mb = folder.getItem('datn') ?: folder.createProject(WorkflowMultiBranchProject, 'datn')
if (mb.getSourcesList().isEmpty()) {
  def src = new GitHubSCMSource('__GH_OWNER__', '__GH_REPO__')
  src.setId('datn-github')
  src.setCredentialsId('github-token')
  src.setTraits([new BranchDiscoveryTrait(1), new OriginPullRequestDiscoveryTrait(1), new ForkPullRequestDiscoveryTrait(1, new ForkPullRequestDiscoveryTrait.TrustNobody())])
  mb.getSourcesList().add(new BranchSource(src))
  mb.addTrigger(new PeriodicFolderTrigger('1d'))
  mb.save()
  println '[datn] đã tạo DATN/datn'
}

// 4) Job smoke-test (doc 01 §12)
if (j.getItem('smoke-test') == null) {
  def st = j.createProject(WorkflowJob, 'smoke-test')
  st.setDefinition(new CpsFlowDefinition('''
pipeline {
  agent any
  tools { jdk 'jdk-21'; maven 'maven-3.9'; nodejs 'node-22' }
  stages {
    stage('Tools') { steps { sh 'java -version && mvn -v && node -v && npm -v && docker version' } }
    stage('Docker Hub login') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DH_USER', passwordVariable: 'DH_TOKEN')]) {
          sh 'echo "$DH_TOKEN" | docker login -u "$DH_USER" --password-stdin && docker logout'
        }
      }
    }
    stage('SSH server ứng dụng') {
      steps { sshagent(['app-server-ssh']) { sh 'ssh -o BatchMode=yes deployer@__APP_SERVER_IP__ check' } }
    }
  }
}
''', true))
  st.save()
  println '[datn] đã tạo smoke-test'
}
println '[datn] init xong'
