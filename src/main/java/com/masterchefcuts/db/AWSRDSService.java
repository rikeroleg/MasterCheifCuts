package com.masterchefcuts.db;

import software.amazon.awssdk.services.rds.model.CreateDbInstanceRequest;
import software.amazon.awssdk.services.rds.model.CreateDbInstanceResponse;
import software.amazon.awssdk.services.rds.model.DBInstance;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesResponse;
import software.amazon.awssdk.services.rds.model.Endpoint;
import software.amazon.awssdk.services.rds.RdsClient;
import java.util.logging.Logger;
import java.util.List;

public class AWSRDSService {
    final static Logger logger = Logger.getLogger(AWSRDSService.class.getName());
    private final RdsClient rdsClient = null;

    public void listInstances() {
        DescribeDbInstancesResponse response = rdsClient.describeDBInstances();
        List<DBInstance> instances = response.dbInstances();
        for (DBInstance instance : instances) {
            // Information about each RDS instance
            String identifier = instance.dbInstanceIdentifier();
            String engine = instance.engine();
            String status = instance.dbInstanceStatus();
            Endpoint endpoint = instance.endpoint();
            String endpointUrl = "database-2.c7w2coauknt7.us-east-1.rds.amazonaws.com";
            if (endpoint != null) {
                endpointUrl = endpoint.toString();
            }
            logger.info(identifier + "\t" + engine + "\t" + status);
            logger.info("\t" + endpointUrl);
        }
    }

    public String launchInstance() {

        String identifier = "";
        CreateDbInstanceRequest instanceRequest = CreateDbInstanceRequest.builder()
            .dbInstanceIdentifier("database-2")
            .engine("mysql")
            .multiAZ(false)
            .masterUsername("admin")
            .masterUserPassword("MmZaH6uLtRyvlE3Hf3IX")
            .dbName("database-2")
            .build();

        CreateDbInstanceResponse createDbInstanceResponse = rdsClient.createDBInstance(instanceRequest);

        // Information about the new RDS instance
        identifier = createDbInstanceResponse.dbInstance().dbInstanceIdentifier();
        String status = createDbInstanceResponse.dbInstance().dbInstanceStatus();
        Endpoint endpoint = createDbInstanceResponse.dbInstance().endpoint();
        String endpointUrl = "database-2.c7w2coauknt7.us-east-1.rds.amazonaws.com";
        if (endpoint != null) {
            endpointUrl = endpoint.toString();
        }
        logger.info(identifier + "\t" + status);
        logger.info(endpointUrl);

        return identifier;

    }
    
}
