/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.ccr.action;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.TaskOperationFailure;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.tasks.TransportTasksAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.license.LicenseUtils;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.ccr.Ccr;
import org.elasticsearch.xpack.ccr.CcrLicenseChecker;
import org.elasticsearch.xpack.core.ccr.action.FollowStatsAction;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class TransportFollowStatsAction extends TransportTasksAction<
        ShardFollowNodeTask,
        FollowStatsAction.StatsRequest,
        FollowStatsAction.StatsResponses, FollowStatsAction.StatsResponse> {

    private final IndexNameExpressionResolver resolver;
    private final CcrLicenseChecker ccrLicenseChecker;

    @Inject
    public TransportFollowStatsAction(
            final Settings settings,
            final ThreadPool threadPool,
            final ClusterService clusterService,
            final TransportService transportService,
            final ActionFilters actionFilters,
            final IndexNameExpressionResolver resolver,
            final CcrLicenseChecker ccrLicenseChecker) {
        super(
                settings,
                FollowStatsAction.NAME,
                threadPool,
                clusterService,
                transportService,
                actionFilters,
                resolver,
                FollowStatsAction.StatsRequest::new,
                FollowStatsAction.StatsResponses::new,
                Ccr.CCR_THREAD_POOL_NAME);
        this.resolver = Objects.requireNonNull(resolver);
        this.ccrLicenseChecker = Objects.requireNonNull(ccrLicenseChecker);
    }

    @Override
    protected void doExecute(
            final Task task,
            final FollowStatsAction.StatsRequest request,
            final ActionListener<FollowStatsAction.StatsResponses> listener) {
        if (ccrLicenseChecker.isCcrAllowed() == false) {
            listener.onFailure(LicenseUtils.newComplianceException("ccr"));
            return;
        }
        super.doExecute(task, request, listener);
    }

    @Override
    protected FollowStatsAction.StatsResponses newResponse(
            final FollowStatsAction.StatsRequest request,
            final List<FollowStatsAction.StatsResponse> statsRespons,
            final List<TaskOperationFailure> taskOperationFailures,
            final List<FailedNodeException> failedNodeExceptions) {
        return new FollowStatsAction.StatsResponses(taskOperationFailures, failedNodeExceptions, statsRespons);
    }

    @Override
    protected FollowStatsAction.StatsResponse readTaskResponse(final StreamInput in) throws IOException {
        return new FollowStatsAction.StatsResponse(in);
    }

    @Override
    protected void processTasks(final FollowStatsAction.StatsRequest request, final Consumer<ShardFollowNodeTask> operation) {
        final ClusterState state = clusterService.state();
        final Set<String> concreteIndices = new HashSet<>(Arrays.asList(resolver.concreteIndexNames(state, request)));
        for (final Task task : taskManager.getTasks().values()) {
            if (task instanceof ShardFollowNodeTask) {
                final ShardFollowNodeTask shardFollowNodeTask = (ShardFollowNodeTask) task;
                if (concreteIndices.contains(shardFollowNodeTask.getFollowShardId().getIndexName())) {
                    operation.accept(shardFollowNodeTask);
                }
            }
        }
    }

    @Override
    protected void taskOperation(
            final FollowStatsAction.StatsRequest request,
            final ShardFollowNodeTask task,
            final ActionListener<FollowStatsAction.StatsResponse> listener) {
        listener.onResponse(new FollowStatsAction.StatsResponse(task.getStatus()));
    }

}