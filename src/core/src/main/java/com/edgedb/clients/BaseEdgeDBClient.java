package com.edgedb.clients;

import com.edgedb.EdgeDBClientConfig;
import com.edgedb.EdgeDBConnection;
import com.edgedb.EdgeDBQueryable;
import com.edgedb.async.AsyncEvent;
import com.edgedb.state.Config;
import com.edgedb.state.ConfigBuilder;
import com.edgedb.state.Session;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class BaseEdgeDBClient implements StatefulClient, EdgeDBQueryable, AutoCloseable {
    private final AsyncEvent<BaseEdgeDBClient> onReady;
    private boolean isConnected = false;
    private final EdgeDBConnection connection;
    private final EdgeDBClientConfig config;
    private final AutoCloseable poolHandle;

    // TODO: remove when 'clients' are no longer exposed
    protected Session session;

    public BaseEdgeDBClient(EdgeDBConnection connection, EdgeDBClientConfig config, AutoCloseable poolHandle) {
        this.connection = connection;
        this.config = config;
        this.session = new Session();
        this.poolHandle = poolHandle;
        this.onReady = new AsyncEvent<>();
    }

    public void onReady(Function<BaseEdgeDBClient, CompletionStage<?>> handler) {
        this.onReady.add(handler);
    }

    protected CompletionStage<Void> dispatchReady() {
        return this.onReady.dispatch(this);
    }

    public abstract Map<String, Object> getServerConfig();

    public abstract Optional<Long> getSuggestedPoolConcurrency();

    void setIsConnected(boolean isConnected) {
        this.isConnected = isConnected;
    }
    public boolean isConnected() {
        return isConnected;
    }

    public EdgeDBConnection getConnection() {
        return this.connection;
    }
    public EdgeDBClientConfig getConfig() {
        return this.config;
    }

    @Override
    public BaseEdgeDBClient withSession(@NotNull Session session) {
        this.session = session;
        return this;
    }

    @Override
    public BaseEdgeDBClient withModuleAliases(@NotNull Map<String, String> aliases) {
        this.session = this.session.withModuleAliases(aliases);
        return this;
    }

    @Override
    public BaseEdgeDBClient withConfig(@NotNull Config config) {
        this.session = this.session.withConfig(config);
        return this;
    }

    @Override
    public BaseEdgeDBClient withConfig(@NotNull Consumer<ConfigBuilder> func) {
        this.session = this.session.withConfig(func);
        return this;
    }

    @Override
    public BaseEdgeDBClient withGlobals(@NotNull Map<String, Object> globals) {
        this.session = this.session.withGlobals(globals);
        return this;
    }

    @Override
    public BaseEdgeDBClient withModule(@NotNull String module) {
        this.session = this.session.withModule(module);
        return this;
    }

    public abstract CompletionStage<Void> connect();
    public abstract CompletionStage<Void> disconnect();

    public CompletionStage<Void> reconnect() {
        return disconnect().thenCompose((v) -> connect());
    }

    @Override
    public void close() throws Exception {
        this.poolHandle.close();
    }
}