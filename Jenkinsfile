def GIT_URL = "https://github.com/FuseByExample/microservice-camel-in-action.git"
def BRANCH = "kubernetes"

stage('clone')
node {
    git url: GIT_URL, branch: BRANCH
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

        sh 'oc login -u admin -p admin https://localhost:8443; oc new-project demo'

        dir('camel-rest-service') {
         sh 'mvn -Pf8-local-deploy'
        }
        
        dir('camel-rest-client') {
         sh 'mvn -Pf8-local-deploy'
        }
        
    }
}
