package com.example.conjur.admin.rest;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/sample-app")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SampleAppResource {

    private static final Logger LOG = Logger.getLogger(SampleAppResource.class.getName());

    @Inject
    @ConfigProperty(name = "conjur.appliance.url",
                    defaultValue = "http://conjur-server.conjur-system.svc.cluster.local")
    private String conjurUrl;

    @Inject
    @ConfigProperty(name = "conjur.account", defaultValue = "nimbusConjurAccount")
    private String conjurAccount;

    private KubernetesClient kube;

    @PostConstruct
    public void init() {
        try {
            kube = new KubernetesClientBuilder().build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "K8s client unavailable", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (kube != null) kube.close();
    }

    @POST
    @Path("/deploy")
    public Map<String, Object> deploy(Map<String, Object> body) {
        if (kube == null) return Map.of("error", "Kubernetes client not available");
        try {
            String appName = (String) body.getOrDefault("appName", "db2-sample");
            String ns = (String) body.getOrDefault("namespace", "apps");
            int nodePort = ((Number) body.getOrDefault("nodePort", 30088)).intValue();
            String jwtServiceId = (String) body.getOrDefault("jwtServiceId", "kubernetes");
            String secretMappings = (String) body.getOrDefault("secretMappings", "");
            String appImage = (String) body.getOrDefault("appImage", "localhost:30500/db2-microprofile-app:latest");
            String initImage = (String) body.getOrDefault("initImage", "localhost:30500/conjur-init:latest");

            // 1. ServiceAccount
            ServiceAccount sa = new ServiceAccountBuilder()
                    .withNewMetadata().withName(appName + "-sa").withNamespace(ns).endMetadata()
                    .build();
            kube.serviceAccounts().inNamespace(ns).resource(sa).serverSideApply();

            // 2. Deployment with init container
            Deployment dep = new DeploymentBuilder()
                    .withNewMetadata().withName(appName).withNamespace(ns)
                        .addToLabels("app", appName).endMetadata()
                    .withNewSpec()
                        .withReplicas(1)
                        .withNewSelector().addToMatchLabels("app", appName).endSelector()
                        .withNewTemplate()
                            .withNewMetadata().addToLabels("app", appName).endMetadata()
                            .withNewSpec()
                                .withServiceAccountName(appName + "-sa")
                                .addNewInitContainer()
                                    .withName("conjur-init")
                                    .withImage(initImage)
                                    .addNewEnv().withName("CONJUR_APPLIANCE_URL").withValue(conjurUrl).endEnv()
                                    .addNewEnv().withName("CONJUR_ACCOUNT").withValue(conjurAccount).endEnv()
                                    .addNewEnv().withName("CONJUR_AUTHN_JWT_SERVICE_ID").withValue(jwtServiceId).endEnv()
                                    .addNewEnv().withName("CONJUR_SECRET_MAPPINGS").withValue(secretMappings).endEnv()
                                    .addNewEnv().withName("CONJUR_SECRETS_DIR").withValue("/conjur/secrets").endEnv()
                                    .addNewVolumeMount().withName("conjur-secrets").withMountPath("/conjur/secrets").endVolumeMount()
                                .endInitContainer()
                                .addNewContainer()
                                    .withName(appName)
                                    .withImage(appImage)
                                    .addNewPort().withContainerPort(9080).endPort()
                                    .addNewEnv().withName("CONJUR_SECRETS_DIR").withValue("/conjur/secrets").endEnv()
                                    .addNewVolumeMount().withName("conjur-secrets").withMountPath("/conjur/secrets").withReadOnly(true).endVolumeMount()
                                    .withNewReadinessProbe()
                                        .withNewHttpGet().withPath("/api/health/ready").withNewPort(9080).endHttpGet()
                                        .withInitialDelaySeconds(20).withPeriodSeconds(10)
                                    .endReadinessProbe()
                                    .withNewResources()
                                        .addToRequests("memory", new Quantity("128Mi"))
                                        .addToRequests("cpu", new Quantity("100m"))
                                        .addToLimits("memory", new Quantity("512Mi"))
                                        .addToLimits("cpu", new Quantity("500m"))
                                    .endResources()
                                .endContainer()
                                .addNewVolume()
                                    .withName("conjur-secrets")
                                    .withNewEmptyDir().withMedium("Memory").endEmptyDir()
                                .endVolume()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                    .build();
            kube.apps().deployments().inNamespace(ns).resource(dep).serverSideApply();

            // 3. NodePort Service
            Service svc = new ServiceBuilder()
                    .withNewMetadata().withName(appName).withNamespace(ns).endMetadata()
                    .withNewSpec()
                        .withType("NodePort")
                        .addToSelector("app", appName)
                        .addNewPort().withPort(9080).withTargetPort(new IntOrString(9080)).withNodePort(nodePort).endPort()
                    .endSpec()
                    .build();
            kube.services().inNamespace(ns).resource(svc).serverSideApply();

            return Map.of("message", "Deployed " + appName,
                    "service", appName, "namespace", ns,
                    "nodePort", nodePort,
                    "url", "http://localhost:" + nodePort);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Deploy failed", e);
            return Map.of("error", "Deploy failed: " + e.getMessage());
        }
    }

    @DELETE
    @Path("/undeploy/{name}")
    public Map<String, Object> undeploy(@PathParam("name") String appName,
                                         @QueryParam("namespace") @DefaultValue("apps") String ns) {
        if (kube == null) return Map.of("error", "Kubernetes client not available");
        try {
            List<String> deleted = new ArrayList<>();
            try { kube.apps().deployments().inNamespace(ns).withName(appName).delete(); deleted.add("deployment"); } catch (Exception ignored) {}
            try { kube.services().inNamespace(ns).withName(appName).delete(); deleted.add("service"); } catch (Exception ignored) {}
            try { kube.serviceAccounts().inNamespace(ns).withName(appName + "-sa").delete(); deleted.add("serviceaccount"); } catch (Exception ignored) {}
            return Map.of("message", "Undeployed " + appName, "deleted", deleted);
        } catch (Exception e) {
            return Map.of("error", "Undeploy failed: " + e.getMessage());
        }
    }

    @GET
    @Path("/status/{name}")
    public Map<String, Object> status(@PathParam("name") String appName,
                                       @QueryParam("namespace") @DefaultValue("apps") String ns) {
        if (kube == null) return Map.of("error", "Kubernetes client not available");
        try {
            Map<String, Object> result = new LinkedHashMap<>();

            Deployment dep = kube.apps().deployments().inNamespace(ns).withName(appName).get();
            if (dep == null) {
                return Map.of("deployed", false, "appName", appName);
            }

            var depStatus = dep.getStatus();
            result.put("deployed", true);
            result.put("appName", appName);
            result.put("replicas", depStatus.getReplicas());
            result.put("readyReplicas", depStatus.getReadyReplicas());
            result.put("availableReplicas", depStatus.getAvailableReplicas());

            // Pod info
            var pods = kube.pods().inNamespace(ns).withLabel("app", appName).list().getItems();
            List<Map<String, Object>> podList = new ArrayList<>();
            for (var pod : pods) {
                Map<String, Object> podInfo = new LinkedHashMap<>();
                podInfo.put("name", pod.getMetadata().getName());
                podInfo.put("phase", pod.getStatus().getPhase());

                // Init container statuses
                if (pod.getStatus().getInitContainerStatuses() != null) {
                    for (var cs : pod.getStatus().getInitContainerStatuses()) {
                        Map<String, Object> initInfo = new LinkedHashMap<>();
                        initInfo.put("name", cs.getName());
                        initInfo.put("ready", cs.getReady());
                        if (cs.getState().getTerminated() != null) {
                            initInfo.put("status", "terminated");
                            initInfo.put("exitCode", cs.getState().getTerminated().getExitCode());
                            initInfo.put("reason", cs.getState().getTerminated().getReason());
                        } else if (cs.getState().getRunning() != null) {
                            initInfo.put("status", "running");
                        } else if (cs.getState().getWaiting() != null) {
                            initInfo.put("status", "waiting");
                            initInfo.put("reason", cs.getState().getWaiting().getReason());
                        }
                        podInfo.put("initContainer", initInfo);
                    }
                }

                // Main container statuses
                if (pod.getStatus().getContainerStatuses() != null) {
                    for (var cs : pod.getStatus().getContainerStatuses()) {
                        Map<String, Object> containerInfo = new LinkedHashMap<>();
                        containerInfo.put("name", cs.getName());
                        containerInfo.put("ready", cs.getReady());
                        containerInfo.put("restartCount", cs.getRestartCount());
                        if (cs.getState().getRunning() != null) containerInfo.put("status", "running");
                        else if (cs.getState().getWaiting() != null) {
                            containerInfo.put("status", "waiting");
                            containerInfo.put("reason", cs.getState().getWaiting().getReason());
                        } else if (cs.getState().getTerminated() != null) {
                            containerInfo.put("status", "terminated");
                        }
                        podInfo.put("container", containerInfo);
                    }
                }
                podList.add(podInfo);
            }
            result.put("pods", podList);

            // Service info
            Service svc = kube.services().inNamespace(ns).withName(appName).get();
            if (svc != null && svc.getSpec().getPorts() != null && !svc.getSpec().getPorts().isEmpty()) {
                result.put("nodePort", svc.getSpec().getPorts().get(0).getNodePort());
            }

            return result;
        } catch (Exception e) {
            return Map.of("error", "Status check failed: " + e.getMessage());
        }
    }

    @GET
    @Path("/logs/{name}")
    public Map<String, Object> logs(@PathParam("name") String appName,
                                     @QueryParam("namespace") @DefaultValue("apps") String ns,
                                     @QueryParam("container") @DefaultValue("conjur-init") String container) {
        if (kube == null) return Map.of("error", "Kubernetes client not available");
        try {
            var pods = kube.pods().inNamespace(ns).withLabel("app", appName).list().getItems();
            if (pods.isEmpty()) return Map.of("error", "No pods found for " + appName);

            String podName = pods.get(0).getMetadata().getName();
            String log = kube.pods().inNamespace(ns).withName(podName).inContainer(container)
                    .tailingLines(100).getLog();
            return Map.of("pod", podName, "container", container, "log", log);
        } catch (Exception e) {
            return Map.of("error", "Log fetch failed: " + e.getMessage());
        }
    }
}
