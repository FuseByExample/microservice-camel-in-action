def GIT_URL = "http://gogs.vagrant.f8/gogsadmin/microservice.git"
def BRANCH = "master"
def CREDENTIALS = ""

#stage('prepare')
#node {
#    sh 'wget https://github.com/openshift/origin/releases/download/v1.1.3/openshift-origin-client-tools-v1.1.3-cffae05-linux-64bit.tar.gz'
#    sh 'tar -vxf openshift-origin-client-tools-v1.1.3-cffae05-linux-64bit.tar.gz'
#    sh 'export OC_PATH=`pwd`/openshift-origin-client-tools-v1.1.3-cffae05-linux-64bit'
#    sh 'export PATH=$PATH:$OC_PATH'
#
#    sh '`pwd`/openshift-origin-client-tools-v1.1.3-cffae05-linux-64bit/oc login -u admin -p admin --insecure-skip-tls-verify=false https://172.17.0.1:8443'
#    sh '`pwd`/openshift-origin-client-tools-v1.1.3-cffae05-linux-64bit/oc new-project demo'
#}

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

        dir('camel-rest-service') {
         sh 'mvn -Dfabric8.namespace=demo -Pf8-local-deploy'
        }

        dir('camel-rest-client') {
         sh 'mvn -Dfabric8.namespace=demo -Pf8-local-deploy'
        }

    }
}