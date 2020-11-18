package io.mycat.config;

import io.mycat.util.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Data
@EqualsAndHashCode
public class ClusterConfig {
    private String clusterType = "MASTER_SLAVE";
    private String switchType = "SWITCH";
    private String readBalanceType = "BALANCE_ALL";
    private String name;
    private String readBalanceName;
    private String writeBalanceName;
    private List<String> masters;
    private List<String> replicas;
    private HeartbeatConfig heartbeat = HeartbeatConfig.builder()
            .minSwitchTimeInterval(300)
            .heartbeatTimeout(1000)
            .slaveThreshold(0)
            .maxRetry(3)
            .build();
    private Integer maxCon = 2000;
    private TimerConfig timer = null;

    public ClusterConfig() {
    }

    public List<String> allDatasources() {
        if (masters == null) {
            masters = Collections.emptyList();
        }
        if (replicas == null) {
            replicas = Collections.emptyList();
        }
        ArrayList<String> nodes = new ArrayList<>(masters.size() + replicas.size());
        nodes.addAll(masters);
        nodes.addAll(replicas);
        return nodes;
    }

    public static void main(String[] args) {
        ClusterConfig clusterConfig = new ClusterConfig();
        System.out.println(JsonUtil.toJson(clusterConfig));
    }

}