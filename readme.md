# Camel REST Microservices 

The MicroService Camel REST in action project consists of 2 modules `camel-rest-client` and `camel-rest-service`; one contains the code to play the role of a client sending HTTP requests and calling a REST Service exposed by a HTTP Server.
They both will be created as Microservice and we will be able to run them :

- Locally with a local JVM
- Within a Docker daemon running into a Linux Atomic Kernel as a docker process
- Within the OpensShift platform using the Kubernetes Controller and Service to loadbalance the requests

The project is maintained under 3 different git branches:

- The `master` branch to run locally using `mvn camel:run` goal the microservices
- The `docker` branch to deploy the docker images within a docker daemon and
- The `kubernetes` to install the controller responsible to manage the pods and service top of the Openshift Platform

This project presents also 2 approaches to package/assemble the microservices and develop a continuous delivery strategy.

![Openshift Microservice](https://raw.githubusercontent.com/FuseByExample/microservice-camel-in-action/master/image/microservice-kubernetes-rest.png)

# Table Of Content

* [Prerequisites](#prerequisites)
* [Project creation](#project-creation)
  * [iPaas Archetype](#ipaas-archetype)
  * [Using JBoss Forge](#using-jboss-forge)
* [Run locally the MicroServices](#run-locally-the-microservices)
* [Use a Docker daemon](#use-a-docker-daemon)
* [Use OpenShift &amp; Kubernetes Service](#use-openshift--kubernetes-service)
* [Package the microservices](#package-the-microservices)
* [Continuous Development](#continuous-development)
* [Clean project](#clean-project)

# Prerequisites

* [Vagrant](https://www.vagrantup.com/)
* [VirtualBox](https://www.virtualbox.org/)
* [Fabric8 Installer](https://github.com/fabric8io/fabric8-installer)
* [Docker Machine](https://docs.docker.com/machine/install-machine/) OR [Fabric8 Installer Vagrant](https://github.com/fabric8io/fabric8-installer/tree/master/vagrant/openshift)
* Apache Maven
* JDK 8

# Project creation

## iPaas Archetype

* Use this Camel Archetype to create the skeleton of the project which is a camel-cdi maven module

```
mvn archetype:generate
...
19: remote -> io.fabric8.archetypes:cdi-camel-http-archetype (Creates a new Camel route using CDI in a standalone Java Container calling the remote camel-servlet quickstart))
...
Using these parameters

Archetype Version : 2.2.88
Archetype name : cdi-camel-http-archetype
Project : camel-rest-client
Package : org.jboss.fuse
Version: 1.0-SNAPSHOT
```

This archetype will be used as input to create a Camel route using the CDI Framework to inject the Beans and start the CamelContext. The purpose
of the Apache Camel Route will be to send every 5s a message a HTTP request to the REST service using a Netty4-HTTP Endpoint as described hereafter.

```
@ContextName("myCdiCamelContext")
public class MyRoutes extends RouteBuilder {

    @Inject
    @Uri("timer:foo?period=5000")
    private Endpoint inputEndpoint;

    @Inject
    /** Local **/
    @Uri("netty4-http:http://localhost:8080?keepalive=false&disconnect=true")

    /** Docker Container **/
    //@Uri("netty4-http:http://{{env:DOCKER_CONTAINER_IP}}:8080?keepalive=false&disconnect=true")

    /** Pod Container + Kubernetes Service  **/
    //@Uri("netty4-http:http://{{service:hellorest}}?keepalive=false&disconnect=true")
    private Endpoint httpEndpoint;

    @Inject
    private SomeBean someBean;

    @Override
    public void configure() throws Exception {
        // you can configure the route rule with Java DSL here

        from(inputEndpoint)
            .setHeader("user").method(someBean,"getRandomUser")
            .setHeader("CamelHttpPath").simple("/camel/users/${header.user}/hello")
            .to(httpEndpoint)
            .log("Response : ${body}");
```

The url of the endpoint will be changed according to the environment where we will run the route: local, docker daemon or openshift v3.

The method `getRandomUser` has been added within the someBean class to generate from a list, the user saying Hello

```
@Singleton
@Named("someBean")
public class SomeBean {

    static List<String> users;

    public SomeBean() {
        users = new ArrayList<String>();
        users.add("James Strachan");
        users.add("Claus Ibsen");
        users.add("Hiram Chirino");
        users.add("Jeff Bride");
        users.add("Chad Darby");
        users.add("Rachel Cassidy");
        users.add("Bernard Tison");
        users.add("Nandan Joshi");
        users.add("Rob Davies");
        users.add("Guillaume Nodet");
        users.add("Marc Little");
        users.add("Mario Fusco");
        users.add("James Hetfield");
        users.add("Kirk Hammett");
        users.add("Steve Perry");
    }

    private int counter;

    public static String getRandomUser() {
        //0-11
        int index = new Random().nextInt(users.size());
        return users.get(index);
    }

```

The next project will be designed using the camel web archetype which is a Servlet Tomcat application and will be used to expose using the Camel REST DSL
a REST service to get a User Hello Message.

The REST GET Service is defined as such : `/camel/users/${id_of_the_user}/hello` and this message will be returned `'"Hello " + id + "! Welcome from pod/docker host : " + System.getenv("HOSTNAME")'`

Here is the syntax of the Camel Route to be used

```
public class CamelRestRoute extends RouteBuilder {

    private static final String HOST = "0.0.0.0";
    private static final String PORT = "8080";

    @Override public void configure() throws Exception {

        restConfiguration().component("servlet").host(HOST).setPort(PORT);

        // use the rest DSL to define the rest services
        rest("/users/")
                .get("{id}/hello")
                .route()
                .process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String id = exchange.getIn().getHeader("id", String.class);
                        exchange.getOut().setBody("Hello " + id + "! Welcome from pod : " + System.getenv("HOSTNAME") );
                    }
                });

```

Add the `<package>` XML tag within the `Camel XML Bean file`

```
 <camelContext xmlns="http://camel.apache.org/schema/spring">
    <package>org.jboss.fuse</package>
  </camelContext>
```

The detail to be used to set the maven archetype is defined hereafter:

```
mvn archetype:generate
51: remote -> io.fabric8.archetypes:war-camel-servlet-archetype (Creates a new Camel route using Servlet deployed as WAR)

Archetype Version : 2.2.88
Archetype name : war-camel-servlet-archetype
Project : camel-rest-service
Package : org.jboss.fuse
Version: 1.0-SNAPSHOT
```

Remarks : We will use the following images (s2i-java for the camel cdi project and tomcat-8.0 for the camel web project) if this is not yet the case

```
      <docker.from>fabric8/s2i-java:1.2</docker.from>
      <docker.from>fabric8/tomcat-8.0</docker.from>
```

## Using JBoss Forge

* Instead of iPaas Archetypes, we will use the standard Camel Archetypes to create the skeleton of the project and next we will run the JBoss Forge command to setup the project

```
mvn archetype:generate
58: remote -> org.apache.camel.archetypes:camel-archetype-cdi (Creates a new Camel project using CDI.)

with these parameters

Project : camel-rest-client
Package : org.jboss.fuse
Version: 1.0-SNAPSHOT
```
and

```
mvn archetype:generate
19: remote -> io.fabric8.archetypes:cdi-camel-http-archetype (Creates a new Camel route using CDI in a standalone Java Container calling the remote camel-servlet quickstart))

with these parameters

Project : camel-rest-service
Package : org.jboss.fuse
Version: 1.0-SNAPSHOT

```
* Next, we will run the `fabric8-setup` forge commands within each maven module created. This command will to add the `Docker/Fabric8 maven plugins` and will update the maven properties with
  the information required by the maven plugins.

* Due to some issues discovered with the latest JBoss Forge fabric8-setup, some adjustments have been required as described here after
* Move `<name>` and `<from>` tags within the docker configuration of the Docker maven plugin and change to the version of the Docker maven plugin to 0.13.6

```
<plugin>
  <groupId>org.jolokia</groupId>
  <artifactId>docker-maven-plugin</artifactId>
  <version>${docker.maven.plugin.version}</version>
  <configuration>
    <images>
      <image>
        <name>${docker.image}</name>
        <build>
          <from>${docker.from}</from>
```
* We will use the following images instead of the images added by the fabric8-setup forge command `fabric8/s2i-java` and `fabric8/tomcat-8`

```
      <docker.from>fabric8/s2i-java:1.2</docker.from>
      <docker.from>fabric8/tomcat-8.0</docker.from>
```

# Run locally the MicroServices

![Openshift Microservice](https://raw.githubusercontent.com/FuseByExample/microservice-camel-in-action/master/image/microservice-rest.png)

* Open 2 terminal and move to the projects; `camel-rest-client` and `camel-rest-service`
* Launch the REST Service

```
mvn jetty:run
```
* And the client

```
mvn camel:run
```
* Test it using curl or HTTPie tool

```
http GET http://localhost:8080/camel/users/charles/hello
HTTP/1.1 200 OK
Date: Mon, 18 Jan 2016 17:39:24 GMT
Server: Jetty(9.2.11.v20150529)
Transfer-Encoding: chunked

Hello charles! Welcome from pod : null

```
# Use a Docker daemon

![Openshift Microservice](https://raw.githubusercontent.com/FuseByExample/microservice-camel-in-action/master/image/microservice-docker-rest.png)

* Launch the docker-machine in a terminal and start the default virtual machine using this command `docker-machine start default`

* Alternatively, you can use the Vagrant Fabric8 virtual Machine created in VirtualBox to access Docker daemon

```
cd /path/to/fabric8-installer/tree/master/vagrant/openshift
vagrant up
```

* Within the terminal where your development projects has been created, set the ENV variables required to access and communicate with the
  Docker daemon by executing this command `eval $(docker-machine env default)`.

* Add the `DOCKER_IP` and `DOCKER_REGISTRY` env variables. The IP address could be different on your machine depending if you use docker-machine, fabric8-vagrant or another docker runtime.

```
docker-machine
    export DOCKER_IP=192.168.99.100
    export DOCKER_REGISTRY="192.168.99.100:5000"
fabric8-vagrant
    export DOCKER_IP=172.28.128.4
    export DOCKER_HOST="tcp://172.28.128.4:2375"
    export DOCKER_REGISTRY="172.28.128.4:5000"
```

* Check that the DOCKER env variables have been created. The information reported could be different according to your environement.

```
docker-machine
    export | grep 'DOCKER'
    export DOCKER_TLS_VERIFY="1"
    export DOCKER_HOST="tcp://192.168.99.100:2376"
    export DOCKER_CERT_PATH="/Users/chmoulli/.docker/machine/machines/default"
    export DOCKER_MACHINE_NAME="default"
fabric8-vagrant
    export | grep 'DOCKER'
    declare -x DOCKER_HOST="tcp://172.28.128.4:2375"
    declare -x DOCKER_IP="172.28.128.4"
    declare -x DOCKER_REGISTRY="172.28.128.4:5000"
```

* Redirect the traffic from the Host to the Docker Virtual Machine as we will access the service from the host or within a container

```
    sudo route -n delete 172.0.0.0/8
    sudo route -n add 172.0.0.0/8 $DOCKER_IP
```

* If this is not yet the case, install a docker registry within your docker daemon as we have to push our build (= docker tar files) to this local registry

```
    docker run -d -p 5000:5000 --restart=always --name registry registry:2
```

* As we will publish our image into a local registry and not on docker.io, we will add/change the following properties of the pom.xml file of the 2 projects

```
    <docker.image>${docker.registryPrefix}fabric8/${project.artifactId}:${project.version}</docker.image>
    <docker.registryPrefix>${env.DOCKER_REGISTRY}/</docker.registryPrefix>
```

* Now, you can build the docker image of the Camel Rest Service and push it to the registry by executing these commands within the terminal of the `camel-rest-service` project.

```
    git checkout -b docker
    mvn clean install docker:build
    docker run -it -p 8080:8080 -p 8778:8778 --name camel-rest-service $DOCKER_IP:5000/fabric8/camel-rest-service:1.0-SNAPSHOT
```

* Find the IP address of the docker container created as we have to change this address for the URL of the client

```
    docker ps --filter="name=rest" | awk '{print $1}' | xargs docker inspect | grep "IPAddress"
```

* Verify that the docker container is running and the port assigned

```
docker ps --filter="name=rest"
CONTAINER ID        IMAGE                                                         COMMAND                  CREATED             STATUS              PORTS                                            NAMES
6da09e192031        192.168.99.100:5000/fabric8/camel-rest-service:1.0-SNAPSHOT   "/bin/sh -c /opt/tomc"   8 minutes ago       Up 8 minutes        0.0.0.0:8080->8080/tcp, 0.0.0.0:8778->8778/tcp   camel-rest-service
```

* Create an env variable with the IP address of the container

```
export DOCKER_CONTAINER_IP=172.17.0.13
```

* Test it using HTTPie or curl. The return message mentions the POD name but the id corresponds to the docker container created

```
http GET http://$DOCKER_CONTAINER_IP:8080/camel/users/charles/hello
HTTP/1.1 200 OK
Date: Mon, 18 Jan 2016 17:57:55 GMT
Server: Apache-Coyote/1.1
Transfer-Encoding: chunked

Hello charles! Welcome from pod : 6da09e192031
```

* We will now deploy the Camel REST client as a microcontainer within the Docker container
* So, build the Image and create the container as such

```
    cd camel-rest-client
    mvn clean install docker:build
    docker run -it --name camel-rest-client -e DOCKER_CONTAINER_IP=172.17.0.13 $DOCKER_IP:5000/fabric8/camel-rest-client:1.0-SNAPSHOT
```

* You should get from the console the messages received from the REST Service

```
    2016-01-18 18:06:22,201 [main           ] INFO  CdiCamelContext                - Route: route1 started and consuming from: Endpoint[timer://foo?period=5000]
    2016-01-18 18:06:22,268 [main           ] INFO  Bootstrap                      - WELD-ENV-002003: Weld SE container STATIC_INSTANCE initialized
    2016-01-18 18:06:23,468 [ClientTCPWorker] INFO  route1                         - Response : Hello Rob Davies! Welcome from pod : 6da09e192031
```


* Connect to the Tomcat console and add the hawtio war to discover the Camel Plugin

```
    http://172.17.0.2:8080/manager/html

    The IP address depends on the address generated by the docker container
    User / password : admin/admin

    Install the hawtio-web.war file available here :                  http://repo1.maven.org/maven2/io/hawt/hawtio-web/1.4.59/hawtio-web-1.4.59.war
```

* You can access now to your Camel routes

```
    http://172.17.0.7:8080/hawtio-web-1.4.59/welcome
```

![Openshift Microservice](https://raw.githubusercontent.com/FuseByExample/microservice-camel-in-action/master/image/camel-docker-plugin.png)

# Use OpenShift & Kubernetes Service

![Openshift Microservice](https://raw.githubusercontent.com/FuseByExample/microservice-camel-in-action/master/image/microservice-kubernetes-rest.png)


* Install the [https://github.com/fabric8io/fabric8-installer](fabric8-installer) project and run `vagrant up` command within the folder vagrant/openshift

* Using the OpenShift client tool, log to the server and create a demo namespace/project

```
    oc login -u admin -p admin https://172.28.128.4:8443
    oc new-project demo
```

* Edit the pom file of the `camel-rest-service` project and update the Fabric8 properties.
* We will create a Kubernetes Service `hellosrest` and assign the external/internal port numbers and setup the loadbalancing type

```
    <fabric8.service.name>hellorest</fabric8.service.name>
    <fabric8.service.port>9090</fabric8.service.port>
    <fabric8.service.containerPort>8080</fabric8.service.containerPort>
    <fabric8.service.type>LoadBalancer</fabric8.service.type>
```

* Next, we will define the labels, container type and add a reference to the camel icon

```
    <fabric8.label.component>${project.artifactId}</fabric8.label.component>
    <fabric8.label.container>tomcat</fabric8.label.container>
    <fabric8.label.group>demo</fabric8.label.group>
    <fabric8.iconRef>camel</fabric8.iconRef>
```

* We can now build a new docker image and generate the kubernetes json file describing the pod / application to be deployed.
* First, we will add new env variables to define the Kubernetes domain & openshift server to call

```
    export KUBERNETES_DOMAIN=vagrant.f8
    export DOCKER_HOST=tcp://172.28.128.4:2375
    export DOCKER_IP=172.28.128.4
    export DOCKER_REGISTRY="172.28.128.4:5000"
```

* We will build the project using the following profile

```
    git checkout -b kubernetes
    mvn -Pf8-build
```

* To deploy the project on OpenShift and to create the pods, then use this command

```
    mvn -Pf8-local-deploy
```

* Update also the properties if the camel-rest-client pom file too

```
    <fabric8.label.component>${project.artifactId}</fabric8.label.component>
    <fabric8.label.container>java</fabric8.label.container>
    <fabric8.label.group>demo</fabric8.label.group>
    <fabric8.iconRef>camel</fabric8.iconRef>
```

* As we will use the Kubernetes `hellorest` service, we will change the url of the client in order to contact the Kubernetes Service / Loadbalancer to dispatch the request of the client to one of the pods running the Camel REST Service

```
    @Uri("netty4-http:http://{{service:hellorest}}?keepalive=false&disconnect=true")
    private Endpoint httpEndpoint;
```

* Build and deploy the pod of the Camel REST Client

```
    mvn -Pf8-local-deploy
```

* Verify in OpenShift / Fabric console that the pods, service, controller have been created

```
oc get pods
NAME                       READY     STATUS    RESTARTS   AGE
camel-rest-client-4ej1q    1/1       Running   0          46s
camel-rest-service-4u9v9   1/1       Running   0          3m

 oc get service
NAME                 CLUSTER_IP      EXTERNAL_IP   PORT(S)    SELECTOR                                                                                                      AGE
camel-rest-client    None            <none>        1/TCP      component=camel-rest-client,container=java,group=quickstarts,project=camel-rest-client,provider=fabric8       2m
camel-rest-service   172.30.65.128   <none>        9101/TCP   component=camel-rest-service,container=tomcat,group=quickstarts,project=camel-rest-service,provider=fabric8   5m

oc get rc
CONTROLLER           CONTAINER(S)         IMAGE(S)                                                      SELECTOR                                                                                                                           REPLICAS   AGE
camel-rest-client    camel-rest-client    192.168.99.100:5000/fabric8/camel-rest-client:1.0-SNAPSHOT    component=camel-rest-client,container=java,group=quickstarts,project=camel-rest-client,provider=fabric8,version=1.0-SNAPSHOT       1          2m
camel-rest-service   camel-rest-service   192.168.99.100:5000/fabric8/camel-rest-service:1.0-SNAPSHOT   component=camel-rest-service,container=tomcat,group=quickstarts,project=camel-rest-service,provider=fabric8,version=1.0-SNAPSHOT   1          5m

oc get route
NAME                 HOST/PORT                            PATH      SERVICE              LABELS    TLS TERMINATION
camel-rest-client    camel-rest-client-demo.vagrant.f8              camel-rest-client
camel-rest-service   camel-rest-service-demo.vagrant.f8             camel-rest-service
```

* Look to the log of the pod of the camel-rest-client to control that you get responses

```
2016-01-18 19:37:25,469 [main           ] INFO  CdiCamelContext                - Route: route1 started and consuming from: Endpoint[timer://foo?period=5000]
2016-01-18 19:37:25,521 [main           ] INFO  Bootstrap                      - WELD-ENV-002003: Weld SE container STATIC_INSTANCE initialized
2016-01-18 19:37:26,767 [ClientTCPWorker] INFO  route1                         - Response : Hello James Strachan! Welcome from pod : camel-rest-service-8pbq9
2016-01-18 19:37:31,494 [ClientTCPWorker] INFO  route1                         - Response : Hello Claus Ibsen! Welcome from pod : camel-rest-service-8pbq9
2016-01-18 19:37:36,490 [ClientTCPWorker] INFO  route1                         - Response : Hello Nandan Joshi! Welcome from pod : camel-rest-service-8pbq9
```

* Increase the controller of the REST service to create 3 pods

* Check that the client gets a request from one of the Service running into a different pod

```
2016-01-18 19:39:06,534 [ClientTCPWorker] INFO  route1                         - Response : Hello Claus Ibsen! Welcome from pod : camel-rest-service-zyzec
2016-01-18 19:39:11,532 [ClientTCPWorker] INFO  route1                         - Response : Hello Rob Davies! Welcome from pod : camel-rest-service-f7fw7
2016-01-18 19:39:16,522 [ClientTCPWorker] INFO  route1                         - Response : Hello James Strachan! Welcome from pod : camel-rest-service-8pbq9
```

# Clean project

To clean the `demo` namespace/project, please use the following OpenShift `oc` client commands responsible to remove the pods, services, replication controller & routes

```
oc delete pods -l group=demo
oc delete services -l group=demo
oc delete route -l group=demo
oc delete rc -l group=demo
```

If, for any reasons you would like to delete the `Fabric8` kubernetes applications (Console, Cd Pipeline, ...), you can achieve this goal using these commands :

```
oc delete all -l provider=fabric8
```

# Package the microservices

During the previous section we have used the maven goal :

- `docker:build` to create the docker image, 
- `fabric8:json` goal to create the kubernetes json file containing the information about the replication controller, service and pods to be created and
- the `fabric8:apply` goal has been executed to deploy the project on Openshift for each microservice.

While this approach is relevant when we develop and test microservice individually, this is not longer the case
when the project will be delivered for the different environments where it will run for `testing` and `production` purposes.
It will be required to assemble the microservices together.

To achieve this goal, we will package our `microservices` using an OpenShift Template which is a json file extending the definition of a Kubernetes json file. This file will be created by concatenating
the json files created for the camel REST client and camel REST service. 

For that purpose, we have created a maven `packages` module where the `<packaging>` has been define to `pom` and where we have declared the dependencies of our microservices to be packaged together as such

```
<dependency>
    <groupId>org.jboss.fuse</groupId>
    <artifactId>camel-rest-service</artifactId>
    <version>${project.version}</version>
    <classifier>kubernetes</classifier>
    <type>json</type>
</dependency>
<dependency>
    <groupId>org.jboss.fuse</groupId>
    <artifactId>camel-rest-client</artifactId>
    <version>${project.version}</version>
    <classifier>kubernetes</classifier>
    <type>json</type>
</dependency>
```

By running this maven command `mvn clean install`, we will generate the following OSE Template under the directory target/classes/kubernetes.json

```
{
  "apiVersion" : "v1",
  "kind" : "Template",
  "labels" : { },
  "metadata" : {
    "annotations" : {
      "fabric8.camel-rest-client/iconUrl" : "https://cdn.rawgit.com/fabric8io/fabric8/master/fabric8-maven-plugin/src/main/resources/icons/camel.svg",
      "fabric8.camel-rest-service/iconUrl" : "https://cdn.rawgit.com/fabric8io/fabric8/master/fabric8-maven-plugin/src/main/resources/icons/camel.svg"
    },
    "labels" : { },
    "name" : "camel-microservices"
  },
  "objects" : [ {
    "apiVersion" : "v1",
    "kind" : "Service",
    "metadata" : {
      "annotations" : {
        "prometheus.io/port" : "9779",
        "prometheus.io/scrape" : "true"
      },
      "labels" : {
        "container" : "java",
        "component" : "camel-rest-client",
        "provider" : "fabric8",
        "project" : "camel-rest-client",
        "version" : "1.0-SNAPSHOT",
        "group" : "demo",
        "package" : "camel-microservices"
      },
      "name" : "camel-rest-client"
    },

    ...
    
```

To deploy our application on OpenShift, we will use the OpenShift `oc` client and with the command `process` and pass as parameter the definition of the file `oc process -f target/classes/kubernetes.json | oc create -f -`

The different commands to be used are summarized here after

```
cd packages

export KUBERNETES_DOMAIN=vagrant.f8
export DOCKER_HOST="tcp://172.28.128.4:2375"
export DOCKER_REGISTRY="172.28.128.4:5000"

oc login -u admin -p admin https://172.28.128.4:8443
oc delete project demo
oc new-project demo
oc delete pods -l group=demo
oc delete services -l group=demo
oc delete route -l group=demo
oc delete rc -l group=demo

mvn clean install

oc process -f target/classes/kubernetes.json | oc create -f -
```

You can now verify that the two pods are up and running

```
oc get pods
NAME                       READY     STATUS    RESTARTS   AGE
camel-rest-client-elxt1    1/1       Running   0          34m
camel-rest-service-4h2j4   1/1       Running   0          30m
camel-rest-service-61gq4   1/1       Running   0          34m
camel-rest-service-t33ws   1/1       Running   0          30m

```

# Continuous Development

In order to automate the build, deployment process and the creation of the Microservices as pods on the OpenShift platform, we will create a Jenkins Groovy DSL file that 
 Jenkins will trigger with the pipeline plugin. This file will contain different stages with the commands required to compile, create the docker image, push it to the docker images registry and finally deploy the kubernetes 
json file describing the kubernetes application to be deployed on the platform.
 
Remark : For more information about the Groovy DSL syntax, please use the following links
 
* [Job DSL Plugin](https://github.com/jenkinsci/job-dsl-plugin)
* [Tutorial](https://github.com/jenkinsci/job-dsl-plugin/wiki/Tutorial---Using-the-Jenkins-Job-DSL)
* [Workflow DSL plugin](https://github.com/jenkinsci/workflow-plugin/)
* [Tutorial](https://github.com/jenkinsci/workflow-plugin/blob/master/TUTORIAL.md)
 
The scenario that we will achieve within the script is defined as such :
 
* Stage `clone` : Git clone the project from the gogs repository
* Stage `compile`: Compile the project using maven
* Stage `deploy` : Deploy the project
** Build the docker images for the microservices client & service
** Create the Kubernetes Service, replication controller and pods 

Here is the definition the Groovy DSL file that we will use. 

```
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
```

To setup the environment on openshift & fabric8, here are the steps to be done.

* Create a VM into VirtualBox using the [Vagrantfile](https://github.com/fabric8io/fabric8-installer/tree/master/vagrant/openshift) of the Fabric8 project

```
cd /path/to/vagrant/openshift
vagrant up
vagrant ssh

sudo bash
gofabric8 pull cd-pipeline
```

* Next, log on to the OpenShift Web Console within your Web Browser at this address `https://172.28.128.4:8443` with the user `admin` and password `admin`
* Click on the `default` project to be redirected to this [link](https://172.28.128.4:8443/console/project/default/overview)
* From the list of the pods, click on `fabric8.vagrant.f8` link to open the fabric8 console within another tab window.
* Log on using the user `admin` and password `admin`
* From the workspace screen, select also the namespace `default`
* From the left menu bar select `Runtime` -  http://fabric8.vagrant.f8/kubernetes/namespace/default/apps?q=
* You will see the list of the pods deployed and running in openshift
* To create the docker containers required to use the continuous delivery process, we have to install some kubernetes applications. This process will be simplified by using 
within this [screen](http://fabric8.vagrant.f8/kubernetes/namespace/default/apps?q=) the button `Run`
* Click on the `Run` button and move within the table to the `cd-pipeline`
* Click on the `green` button to install it
* Review the parameters defined per default and click on the `Run` button.
* You will be redirect to the previous screen and after a few moments, you will see new pods within your list (jenkins, gogs, nexus)
* Click on the `gogs` [link](http://gogs.vagrant.f8/) to access to the Gogs Server 
* Create a new repository with the name `microservice`. The username and password to be used are `gogsadmin` and `RedHat$1`
* Use the `http://gogs.vagrant.f8/gogsadmin/microservice.git` uri to create a new project on your machine and copy/paste within this project this microservice project (without the .git folder)
* Commit the project to gogs
* From the Fabric8 list of pods screen, click on the link to open the `jenkins` server (http://jenkins.vagrant.f8/)
* Increase the number of executors by editing the jenkins system configuration at this address `http://jenkins.vagrant.f8/manage` and change the value from 0 to 1 for the field ``
* Create a new job with the name `microservice`, select the `pipeline` option and clock o nthe button `ok`
* Within the pipeline screen, move to the section `Pipeline script` and add the content of the `jenkinsfile` within the field. Click on the `save` button
* Launch the job and check the content of console to verify that the project is well compiled, ...

```
[INFO] --- fabric8-maven-plugin:2.2.96:apply (default-cli) @ camel-rest-client ---
[INFO] Using kubernetes at: https://kubernetes.default.svc/ in namespace demo
[INFO] Kubernetes JSON: /var/jenkins_home/workspace/microservice/camel-rest-client/target/classes/kubernetes.json
[INFO] OpenShift platform detected
[INFO] Using namespace: demo
[INFO] Creating a Template from kubernetes.json namespace demo name camel-rest-client
[INFO] Created Template: camel-rest-client/target/fabric8/applyJson/demo/template-camel-rest-client.json
[INFO] Looking at repo with directory /var/jenkins_home/workspace/microservice/.git
[INFO] Looking at repo with directory /var/jenkins_home/workspace/microservice/.git
[INFO] Creating a Service from kubernetes.json namespace demo name camel-rest-client
[INFO] Created Service: camel-rest-client/target/fabric8/applyJson/demo/service-camel-rest-client.json
[INFO] Creating a ReplicationController from kubernetes.json namespace demo name camel-rest-client
[INFO] Created ReplicationController: camel-rest-client/target/fabric8/applyJson/demo/replicationcontroller-camel-rest-client.json
[INFO] Creating Route demo:camel-rest-client host: 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 36.795 s
[INFO] Finished at: 2016-03-10T11:00:29+00:00
[INFO] Final Memory: 46M/515M
[INFO] ------------------------------------------------------------------------
```

* Return to the Fabric8 console, select the demo namespace and access to your different pods

Enjoy the Camel MicroService & MicroContainer !
