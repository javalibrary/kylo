package com.thinkbiganalytics.datalake.authorization;

import com.thinkbiganalytics.datalake.authorization.config.RangerConnection;
import com.thinkbiganalytics.datalake.authorization.model.HadoopAuthorizationGroup;
import com.thinkbiganalytics.datalake.authorization.model.HadoopAuthorizationPolicy;
import com.thinkbiganalytics.datalake.authorization.rest.client.RangerRestClient;
import com.thinkbiganalytics.datalake.authorization.rest.client.RangerRestClientConfig;
import com.thinkbiganalytics.datalake.authorization.rest.model.RangerCreatePolicy;
import com.thinkbiganalytics.datalake.authorization.rest.model.RangerGroup;
import com.thinkbiganalytics.datalake.authorization.rest.model.RangerUpdatePolicy;
import com.thinkbiganalytics.metadata.api.event.MetadataEventListener;
import com.thinkbiganalytics.metadata.api.event.MetadataEventService;
import com.thinkbiganalytics.metadata.api.event.feed.FeedPropertyChangeEvent;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

public class RangerAuthorizationService implements HadoopAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(RangerAuthorizationService.class);

    private static final String HADOOP_AUTHORIZATION_TYPE_RANGER = "RANGER";
    private static final String HDFS_REPOSITORY_TYPE = "hdfs";
    private static final String HIVE_REPOSITORY_TYPE = "hive";
    private static final String IsEnable = "true";
    private static final String IsRecursive = "true";
    private static final String IsAuditable = "true";
    private static final String REPOSITORY_TYPE = "repositoryType";
    private static final String POLICY_NAME = "policyName";
    private static final String HIVE_COLUMN_PERMISSION = "*";
    private static final String NIFI = "nifi_";
    private static final String HDFS_READ_ONLY_PERMISSION="read";
    private static final String HIVE_READ_ONLY_PERMISSION="select";

    private RangerRestClient rangerRestClient;
    private RangerConnection rangerConnection;

    /** Event listener for precondition events */
    private final MetadataEventListener<FeedPropertyChangeEvent> feedPropertyChangeListener = new FeedPropertyChangeDispatcher();

    @Inject
    private MetadataEventService metadataEventService;

    /**
     * Adds listeners for transferring events.
     */
    @PostConstruct
    public void addEventListener() {
        metadataEventService.addListener(feedPropertyChangeListener);
    }

    /**
     * Removes listeners and stops transferring events.
     */
    @PreDestroy
    public void removeEventListener() {
        metadataEventService.removeListener(feedPropertyChangeListener);
    }

    /**
     * Implement Ranger Authentication Service. Initiate RangerClient and RangerClientConfig for initializing service and invoke different methods of it.
     */
    @Override
    public void initialize(AuthorizationConfiguration config) {
        rangerConnection = (RangerConnection) config;
        RangerRestClientConfig rangerClientConfiguration = new RangerRestClientConfig(rangerConnection.getHostName(), rangerConnection.getUsername(), rangerConnection.getPassword());
        rangerClientConfiguration.setPort(rangerConnection.getPort());
        rangerRestClient = new RangerRestClient(rangerClientConfiguration);
    }


    @Override
    public RangerGroup getGroupByName(String groupName) {
        return rangerRestClient.getGroupByName(groupName);
    }

    @Override
    public List<HadoopAuthorizationGroup> getAllGroups() {
        return rangerRestClient.getAllGroups();
    }


    @Override
    public void createReadOnlyPolicy(String categoryName, String feedName , List<String> securityGroupNames, List<String> hdfsPaths,
                                     String datebaseName, List<String> tableNames) {

        RangerCreatePolicy rangerCreatePolicy = new RangerCreatePolicy();

        /**
         * Create HDFS Policy
         */
        List<String> hdfsPermissions = new ArrayList();
        hdfsPermissions.add(HDFS_READ_ONLY_PERMISSION);
        String rangerHdfsPolicyName = NIFI + categoryName +"_"+ feedName + "_" + HDFS_REPOSITORY_TYPE;
        String description = "Ranger policy created for group list " + securityGroupNames.toString() + " for resource " + hdfsPaths.toString();
        String hdfsResource = convertListToString(hdfsPaths, ",");

        rangerCreatePolicy.setPolicyName(rangerHdfsPolicyName);
        rangerCreatePolicy.setResourceName(hdfsResource);
        rangerCreatePolicy.setDescription(description);
        rangerCreatePolicy.setRepositoryName(rangerConnection.getHdfsRepositoryName());
        rangerCreatePolicy.setRepositoryType(HDFS_REPOSITORY_TYPE);
        rangerCreatePolicy.setIsEnabled(IsEnable);
        rangerCreatePolicy.setIsRecursive(IsRecursive);
        rangerCreatePolicy.setIsAuditEnabled(IsAuditable);
        rangerCreatePolicy.setPermMapList(securityGroupNames, hdfsPermissions);

        try {
            rangerRestClient.createPolicy(rangerCreatePolicy);
        } catch(Exception e) {
            log.error("Error creating HDFS Ranger policy", e);
            throw new RuntimeException("Error creating HDFS Ranger policy", e);
        }

        /**
         * Creating Hive Policy
         */
        List<String> hivePermissions = new ArrayList();
        hivePermissions.add(HIVE_READ_ONLY_PERMISSION);
        String rangerHivePolicyName = NIFI + categoryName +"_"+ feedName + "_" + HIVE_REPOSITORY_TYPE;
        String hiveDescription = "Ranger policy created for group list " + securityGroupNames.toString() + " for resource " + hdfsPaths.toString();
        String hiveDatabases = datebaseName;
        String hiveTables = convertListToString(tableNames, ",");

        rangerCreatePolicy = new RangerCreatePolicy();

        rangerCreatePolicy.setPolicyName(rangerHivePolicyName);
        rangerCreatePolicy.setDatabases(hiveDatabases);
        rangerCreatePolicy.setTables(hiveTables);
        rangerCreatePolicy.setColumns(HIVE_COLUMN_PERMISSION);
        rangerCreatePolicy.setUdfs("");
        rangerCreatePolicy.setDescription(hiveDescription);
        rangerCreatePolicy.setRepositoryName(rangerConnection.getHiveRepositoryName());
        rangerCreatePolicy.setRepositoryType(HIVE_REPOSITORY_TYPE);
        rangerCreatePolicy.setIsAuditEnabled(IsAuditable);
        rangerCreatePolicy.setIsEnabled(IsEnable);
        rangerCreatePolicy.setPermMapList(securityGroupNames, hivePermissions);

        try {
            rangerRestClient.createPolicy(rangerCreatePolicy);
        } catch(Exception e) {
            log.error("Error creating Hive Ranger policy", e);
            throw new RuntimeException("Error creating Hive Ranger policy", e);
        }

    }

    @Override
    public void updateReadOnlyPolicy(String categoryName, String feedName ,List<String> groups, List<String> hdfsPaths, 
                                     String datebaseNames, List<String> tableNames) throws Exception {

        int policyId = 0;
        String rangerHdfsPolicyName = NIFI + categoryName +"_"+ feedName + "_" + HDFS_REPOSITORY_TYPE;

        Map<String, Object> searchHDFSCriteria = new HashMap<>();
        searchHDFSCriteria.put(POLICY_NAME, rangerHdfsPolicyName);
        searchHDFSCriteria.put(REPOSITORY_TYPE, HDFS_REPOSITORY_TYPE);
        List<HadoopAuthorizationPolicy> hadoopPolicyList = this.searchPolicy(searchHDFSCriteria);

        if (hadoopPolicyList.size() == 0) {
            throw new UnsupportedOperationException("Ranger Plugin : Unable to get ID for Ranger HDFS Policy");
        } else {
            if (hadoopPolicyList.size() > 1) {
                throw new Exception("Unable to find HDFS unique policy.");
            } else {

                for (HadoopAuthorizationPolicy hadoopPolicy : hadoopPolicyList) {
                    policyId = hadoopPolicy.getPolicyId();
                }
            }
        }

        RangerUpdatePolicy rangerUpdatePolicy = new RangerUpdatePolicy();

        /**
         * Update HDFS Policy
         */
        List<String> hdfsPermissions = new ArrayList();
        hdfsPermissions.add(HDFS_READ_ONLY_PERMISSION);

        String description = "Ranger policy updated for group list " + groups.toString() + " for resource " + hdfsPaths.toString();
        String hdfs_resource = convertListToString(hdfsPaths, ",");

        rangerUpdatePolicy.setPolicyName(rangerHdfsPolicyName);
        rangerUpdatePolicy.setResourceName(hdfs_resource);
        rangerUpdatePolicy.setDescription(description);
        rangerUpdatePolicy.setRepositoryName(rangerConnection.getHdfsRepositoryName());
        rangerUpdatePolicy.setRepositoryType(HDFS_REPOSITORY_TYPE);
        rangerUpdatePolicy.setIsEnabled(IsEnable);
        rangerUpdatePolicy.setIsRecursive(IsRecursive);
        rangerUpdatePolicy.setIsAuditEnabled(IsAuditable);
        rangerUpdatePolicy.setPermMapList(groups, hdfsPermissions);

        try
        {
            rangerRestClient.updatePolicy(rangerUpdatePolicy, policyId);
        }catch(Exception e)
        {
            log.error("Failed to update HDFS policy" ,e);
            throw new RuntimeException("Failed to update HDFS policy" ,e);
        }

        /**
         * Update Hive Policy
         */

        String rangerHivePolicyName = NIFI + categoryName +"_"+ feedName + "_" + HIVE_REPOSITORY_TYPE;
        Map<String, Object> searchHiveCriteria = new HashMap<>();
        searchHiveCriteria.put(POLICY_NAME, rangerHivePolicyName);
        searchHiveCriteria.put(REPOSITORY_TYPE, HIVE_REPOSITORY_TYPE);
        hadoopPolicyList = this.searchPolicy(searchHiveCriteria);

        rangerUpdatePolicy = new RangerUpdatePolicy();
        policyId = 0;
        if (hadoopPolicyList.size() == 0) {
            throw new UnsupportedOperationException("Ranger Plugin : Unable to get ID for Ranger Hive Policy");
        } else {
            if (hadoopPolicyList.size() > 1) {
                throw new Exception("Unable to find Hive unique policy.");
            } else {

                for (HadoopAuthorizationPolicy hadoopPolicy : hadoopPolicyList) {
                    policyId = hadoopPolicy.getPolicyId();
                }
            }
        }

        List<String> hivePermissions = new ArrayList();
        hivePermissions.add(HIVE_READ_ONLY_PERMISSION);
        String hiveDescription = "Ranger policy updated for group list " + groups.toString() + " for resource " + datebaseNames.toString();
        String hiveTables = convertListToString(tableNames, ",");

        rangerUpdatePolicy.setPolicyName(rangerHivePolicyName);
        rangerUpdatePolicy.setDatabases(datebaseNames);
        rangerUpdatePolicy.setTables(hiveTables);
        rangerUpdatePolicy.setColumns(HIVE_COLUMN_PERMISSION);
        rangerUpdatePolicy.setUdfs("");
        rangerUpdatePolicy.setDescription(hiveDescription);
        rangerUpdatePolicy.setRepositoryName(rangerConnection.getHiveRepositoryName());
        rangerUpdatePolicy.setRepositoryType(HIVE_REPOSITORY_TYPE);
        rangerUpdatePolicy.setIsAuditEnabled(IsAuditable);
        rangerUpdatePolicy.setIsEnabled(IsEnable);
        rangerUpdatePolicy.setPermMapList(groups, hivePermissions);

        try {
            rangerRestClient.updatePolicy(rangerUpdatePolicy, policyId);
        } catch (Exception e) {
            log.error("Failed to update Hive Policy" ,e);
            throw new RuntimeException("Failed to update Hive Policy" ,e);
        }

    }

    @Override
    public List<HadoopAuthorizationPolicy> searchPolicy(Map<String, Object> searchCriteria) {
        return rangerRestClient.searchPolicies(searchCriteria);
    }

    /**
     * @return : comma separated string
     */
    public static String convertListToString(List<String> list, String delim) {

        StringBuilder sb = new StringBuilder();

        String loopDelim = "";

        for (String input : list) {

            sb.append(loopDelim);
            sb.append(input);

            loopDelim = delim;
        }
        return sb.toString();
    }


    @Override
    public void deletePolicy(String categoryName, String feedName , String repositoryType) throws Exception {

        int policyId = 0;

        repositoryType = repositoryType.toLowerCase();
        String rangerHDFSPolicyName = NIFI + categoryName +"_"+ feedName + "_" + repositoryType;

        Map<String, Object> searchHDFSCriteria = new HashMap<>();
        searchHDFSCriteria.put(POLICY_NAME, rangerHDFSPolicyName);
        searchHDFSCriteria.put(REPOSITORY_TYPE, repositoryType);
        List<HadoopAuthorizationPolicy> hadoopPolicyList = this.searchPolicy(searchHDFSCriteria);

        if (hadoopPolicyList.size() == 0) {
            throw new UnsupportedOperationException("Ranger Plugin : Unable to get ID for Ranger " + rangerHDFSPolicyName + " Policy");
        } else {
            if (hadoopPolicyList.size() > 1) {
                throw new Exception("Unable to find HDFS unique policy.");
            } else {

                for (HadoopAuthorizationPolicy hadoopPolicy : hadoopPolicyList) {
                    policyId = hadoopPolicy.getPolicyId();
                }
            }
        }

        try {
            rangerRestClient.deletePolicy(policyId);
        } catch (Exception e) {
            log.error("Unable to delete policy" ,e);
            throw new RuntimeException("Unable to delete policy" ,e);
        }

    }

    @Override
    public String getType() {
        return HADOOP_AUTHORIZATION_TYPE_RANGER;
    }

    private class FeedPropertyChangeDispatcher implements MetadataEventListener<FeedPropertyChangeEvent> {
        private static final String REGISTRATION_HDFS_FOLDERS = "nifi:registration:hdfsFolders";
        private static final String REGISTRATION_HIVE_SCHEMA = "nifi:registration:hiveSchema";
        private static final String REGISTRATION_HIVE_TABLES = "nifi:registration:tableNames";
        private static final String KYLO_POLICY_PREFIX = "kylo_";

        @Override
        public void notify(final FeedPropertyChangeEvent metadataEvent) {
            if (metadataEvent.getHadoopSecurityGroupNames() != null && hadoopAuthorizationChangesRequired(metadataEvent)) {
                try {
                    validateFieldsNotNull(metadataEvent.getNewProperties().getProperty(REGISTRATION_HIVE_TABLES), metadataEvent.getNewProperties().getProperty(REGISTRATION_HIVE_SCHEMA), metadataEvent.getNewProperties().getProperty(REGISTRATION_HDFS_FOLDERS));

                    String hdfsFoldersWithCommas = metadataEvent.getNewProperties().getProperty(REGISTRATION_HDFS_FOLDERS).replace("\n", ",");
                    Stream<String> hdfsFolders = Stream.of(hdfsFoldersWithCommas);

                    String hiveTablesWithCommas = metadataEvent.getNewProperties().getProperty(REGISTRATION_HIVE_TABLES).replace("\n", ",");
                    Stream<String> hiveTables = Stream.of(hiveTablesWithCommas);
                    String hiveSchema = metadataEvent.getNewProperties().getProperty(REGISTRATION_HIVE_SCHEMA);

                    createReadOnlyPolicy(KYLO_POLICY_PREFIX + metadataEvent.getFeedCategory(), metadataEvent.getFeedName()
                        , metadataEvent.getHadoopSecurityGroupNames()
                        , hdfsFolders.collect(Collectors.toList())
                        , hiveSchema
                        , hiveTables.collect(Collectors.toList()));
                } catch(Exception e) {
                    log.error("Error creating Ranger policy after metadata property change event", e);
                    throw new RuntimeException("Error creating Ranger policy after metadata property change event");
                }
            }
        }

        private void validateFieldsNotNull(String hiveTables, String hiveSchema, String hdfsFolders) {
            if(StringUtils.isEmpty(hiveTables) || StringUtils.isEmpty(hiveSchema) || StringUtils.isEmpty(hdfsFolders)) {
                throw new IllegalArgumentException("Three properties are required in the metadata to create Ranger policies. " + REGISTRATION_HDFS_FOLDERS + ", " + REGISTRATION_HIVE_SCHEMA +
                ", and" +  REGISTRATION_HIVE_TABLES);
            }
        }

        private boolean hadoopAuthorizationChangesRequired(final FeedPropertyChangeEvent metadataEvent) {
            String hdfsFoldersWithCommasNew = metadataEvent.getNewProperties().getProperty(REGISTRATION_HDFS_FOLDERS);
            String hiveTablesWithCommasNew = metadataEvent.getNewProperties().getProperty(REGISTRATION_HIVE_TABLES);
            String hiveSchemaNew = metadataEvent.getNewProperties().getProperty(REGISTRATION_HIVE_SCHEMA);

            String hdfsFoldersWithCommasOld = metadataEvent.getOldProperties().getProperty(REGISTRATION_HDFS_FOLDERS);
            String hiveTablesWithCommasOld = metadataEvent.getOldProperties().getProperty(REGISTRATION_HIVE_TABLES);
            String hiveSchemaOld = metadataEvent.getOldProperties().getProperty(REGISTRATION_HIVE_SCHEMA);

            if(hdfsFoldersChanged(hdfsFoldersWithCommasNew, hdfsFoldersWithCommasOld) || hiveTablesChanged(hiveTablesWithCommasNew, hiveTablesWithCommasOld)
               || hiveSchemaChanged(hiveSchemaNew, hiveSchemaOld)) {
                return true;
            }
            return false;
        }

        private boolean hdfsFoldersChanged(String hdfsFoldersWithCommasNew, String hdfsFoldersWithCommasOld) {
            if(hdfsFoldersWithCommasNew == null && hdfsFoldersWithCommasOld == null) {
                return false;
            }
            else if(hdfsFoldersWithCommasNew == null && hdfsFoldersWithCommasOld != null) {
                return true;
            }
            else if(hdfsFoldersWithCommasNew != null && hdfsFoldersWithCommasOld == null) {
                return true;
            }
            else {
                return !hdfsFoldersWithCommasNew.equals(hdfsFoldersWithCommasOld);
            }
        }

        private boolean hiveTablesChanged(String hiveTablesWithCommasNew, String hiveTablesWithCommasOld) {
            if(hiveTablesWithCommasNew == null && hiveTablesWithCommasOld == null) {
                return false;
            }
            else if(hiveTablesWithCommasNew == null && hiveTablesWithCommasOld != null) {
                return true;
            }
            else if(hiveTablesWithCommasNew != null && hiveTablesWithCommasOld == null) {
                return true;
            }
            else {
                return !hiveTablesWithCommasNew.equals(hiveTablesWithCommasOld);
            }
        }

        private boolean hiveSchemaChanged(String hiveSchemaNew, String hiveSchemaOld) {
            if(hiveSchemaNew == null && hiveSchemaOld == null) {
                return false;
            }
            else if(hiveSchemaNew == null && hiveSchemaOld != null) {
                return true;
            }
            else if(hiveSchemaNew != null && hiveSchemaOld == null) {
                return true;
            }
            else {
                return !hiveSchemaNew.equals(hiveSchemaOld);
            }
        }
    }
}
