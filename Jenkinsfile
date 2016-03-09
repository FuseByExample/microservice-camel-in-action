def GIT_URL = "https://github.com/cmoulliard/camel-webbundle.git"

stage('clone')
node {
    git url: GIT_URL, branch: 'master'
}


stage('test')
node {
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
        sh 'mvn clean test'
    }
}


stage('install')
node {
    withEnv(["PATH+MAVEN=${tool 'maven-3.3.1'}/bin"]) {
        sh 'mvn install'
    }
}
