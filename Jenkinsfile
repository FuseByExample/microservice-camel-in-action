def GIT_URL = "http://gogs.vagrant.f8/gogsadmin/microservice.git"
def BRANCH = "master"
def CREDENTIALS = ""

stage('clone')
node {
    git url: GIT_URL, branch: BRANCH, credentialsId: CREDENTIALS
}


stage('compile')
node {
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
        sh 'mvn clean compile'
    }
}

stage('deploy')
node {
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
         sh 'mvn -Dfabric8.namespace=demo -Pf8-local-deploy'
    }
}