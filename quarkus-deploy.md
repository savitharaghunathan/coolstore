To deploy to Openshift via the Openshift extension

```bash 
oc new-app -e POSTGRESQL_USER=quarkus \                                                                                                             
            -e POSTGRESQL_PASSWORD=quarkus \
            -e POSTGRESQL_DATABASE=coolstore \
            openshift/postgresql:latest \
            --name=coolstore-database
``` 

Make sure that you are logged in via terminal to OpenShift via the `oc` command

```mvn
            mvn clean compile package -Dquarkus.kubernetes.deploy=true
```

