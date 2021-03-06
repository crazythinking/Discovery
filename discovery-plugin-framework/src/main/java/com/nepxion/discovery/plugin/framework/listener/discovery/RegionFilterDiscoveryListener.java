package com.nepxion.discovery.plugin.framework.listener.discovery;

/**
 * <p>Title: Nepxion Discovery</p>
 * <p>Description: Nepxion Discovery</p>
 * <p>Copyright: Copyright (c) 2017-2050</p>
 * <p>Company: Nepxion</p>
 * @author Haojun Ren
 * @version 1.0
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.client.ServiceInstance;

import com.nepxion.discovery.common.entity.DiscoveryEntity;
import com.nepxion.discovery.common.entity.RegionEntity;
import com.nepxion.discovery.common.entity.RegionFilterEntity;
import com.nepxion.discovery.common.entity.RuleEntity;

public class RegionFilterDiscoveryListener extends AbstractDiscoveryListener {
    @Override
    public void onGetInstances(String serviceId, List<ServiceInstance> instances) {
        String consumerServiceId = pluginAdapter.getServiceId();
        String consumerServiceRegion = pluginAdapter.getRegion();

        applyRegionFilter(consumerServiceId, consumerServiceRegion, serviceId, instances);
    }

    private void applyRegionFilter(String consumerServiceId, String consumerServiceRegion, String providerServiceId, List<ServiceInstance> instances) {
        // 如果消费端未配置区域号，那么它可以调用提供端所有服务，需要符合规范，极力避免该情况发生
        if (StringUtils.isEmpty(consumerServiceRegion)) {
            return;
        }

        RuleEntity ruleEntity = pluginAdapter.getRule();
        if (ruleEntity == null) {
            return;
        }

        DiscoveryEntity discoveryEntity = ruleEntity.getDiscoveryEntity();
        if (discoveryEntity == null) {
            return;
        }

        RegionFilterEntity regionFilterEntity = discoveryEntity.getRegionFilterEntity();
        if (regionFilterEntity == null) {
            return;
        }

        Map<String, List<RegionEntity>> regionEntityMap = regionFilterEntity.getRegionEntityMap();
        if (MapUtils.isEmpty(regionEntityMap)) {
            return;
        }

        List<RegionEntity> regionEntityList = regionEntityMap.get(consumerServiceId);
        if (CollectionUtils.isEmpty(regionEntityList)) {
            return;
        }

        // 当前区域的消费端所能调用提供端的区域号列表
        List<String> allNoFilterValueList = null;
        // 提供端规则未作任何定义
        boolean providerConditionDefined = false;
        for (RegionEntity regionEntity : regionEntityList) {
            String providerServiceName = regionEntity.getProviderServiceName();
            if (StringUtils.equalsIgnoreCase(providerServiceName, providerServiceId)) {
                providerConditionDefined = true;

                List<String> consumerRegionValueList = regionEntity.getConsumerRegionValueList();
                List<String> providerRegionValueList = regionEntity.getProviderRegionValueList();

                // 判断consumer-region-value值是否包含当前消费端的区域号
                // 如果consumerRegionValueList为空，表示消费端区域列表未指定，那么任意消费端区域可以访问指定区域提供端区域
                if (CollectionUtils.isNotEmpty(consumerRegionValueList)) {
                    if (consumerRegionValueList.contains(consumerServiceRegion)) {
                        if (allNoFilterValueList == null) {
                            allNoFilterValueList = new ArrayList<String>();
                        }
                        if (CollectionUtils.isNotEmpty(providerRegionValueList)) {
                            allNoFilterValueList.addAll(providerRegionValueList);
                        }
                    } // 这里的条件，在每一次循环都不满足，会让allNoFilterValueList为null，意味着定义的区域关系都不匹配
                } else {
                    if (allNoFilterValueList == null) {
                        allNoFilterValueList = new ArrayList<String>();
                    }
                    if (CollectionUtils.isNotEmpty(providerRegionValueList)) {
                        allNoFilterValueList.addAll(providerRegionValueList);
                    }
                }
            }
        }

        if (allNoFilterValueList != null) {
            // 当allNoFilterValueList为空列表，意味着区域对应关系未做任何定义（即所有的providerRegionValueList为空），不需要执行过滤，直接返回
            if (allNoFilterValueList.isEmpty()) {
                return;
            } else {
                Iterator<ServiceInstance> iterator = instances.iterator();
                while (iterator.hasNext()) {
                    ServiceInstance serviceInstance = iterator.next();
                    String instanceRegion = pluginAdapter.getInstanceRegion(serviceInstance);
                    if (!allNoFilterValueList.contains(instanceRegion)) {
                        iterator.remove();
                    }
                }
            }
        } else {
            if (providerConditionDefined) {
                // 当allNoFilterValueList为null, 意味着定义的区域关系都不匹配，直接清空所有实例
                instances.clear();
            }
        }
    }

    @Override
    public void onGetServices(List<String> services) {

    }

    @Override
    public int getOrder() {
        // After version filter
        return HIGHEST_PRECEDENCE + 2;
    }
}