package com.edgedb;

import com.edgedb.exceptions.ConfigurationException;
import com.edgedb.util.ConfigUtils;
import com.edgedb.util.EnumsUtil;
import com.edgedb.util.QueryParamUtils;
import com.edgedb.util.StringsUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.netty.handler.ssl.SslContextBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

public class EdgeDBConnection implements Cloneable {
    private static final String EDGEDB_INSTANCE_ENV_NAME = "EDGEDB_INSTANCE";
    private static final String EDGEDB_DSN_ENV_NAME = "EDGEDB_DSN";
    private static final String EDGEDB_CREDENTIALS_FILE_ENV_NAME = "EDGEDB_CREDENTIALS_FILE";
    private static final String EDGEDB_USER_ENV_NAME = "EDGEDB_USER";
    private static final String EDGEDB_PASSWORD_ENV_NAME = "EDGEDB_PASSWORD";
    private static final String EDGEDB_DATABASE_ENV_NAME = "EDGEDB_DATABASE";
    private static final String EDGEDB_HOST_ENV_NAME = "EDGEDB_HOST";
    private static final String EDGEDB_PORT_ENV_NAME = "EDGEDB_PORT";

    private static final Pattern DSN_FORMATTER = Pattern.compile("^([a-z]+)://");
    private static final Pattern DSN_QUERY_PARAMETERS = Pattern.compile("((?:.(?!\\?))+$)");
    private static final Pattern DSN_FILE_ARG = Pattern.compile("(.*?)_file");
    private static final Pattern DSN_ENV_ARG = Pattern.compile("(.*?)_env");

    private static final JsonMapper mapper = JsonMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    @JsonProperty("user")
    private String user;

    @JsonProperty("password")
    private String password;

    @JsonProperty("database")
    private String database;

    @JsonIgnore
    private String hostname;

    @JsonProperty("port")
    private Integer port;

    @JsonProperty("tls_ca")
    private String tlsca;

    @JsonProperty("tls_security")
    private @Nullable TLSSecurityMode tlsSecurity;

    public String getUsername() {
        return user == null ? "edgedb" : user;
    }
    public void setUsername(String value) {
        user = value;
    }

    public String getPassword() {
        return password;
    }
    public void setPassword(String value) {
        password = value;
    }

    public String getHostname() {
        return hostname == null ? "127.0.0.1" : hostname;
    }
    public void setHostname(String value) throws ConfigurationException {
        if (value.contains("/")) {
            throw new ConfigurationException("Cannot use UNIX socket for 'Hostname'");
        }

        if (value.contains(",")) {
            throw new ConfigurationException("DSN cannot contain more than one host");
        }

        hostname = value;
    }

    public int getPort() {
        return port == null ? 5656 : port;
    }
    public void setPort(int value) {
        port = value;
    }

    public String getDatabase() {
        return database == null ? "edgedb" : database;
    }
    public void setDatabase(String value) {
        database = value;
    }

    public String getTLSCertificateAuthority() {
        return tlsca;
    }
    public void setTLSCertificateAuthority(String value) {
        tlsca = value;
    }

    public TLSSecurityMode getTLSSecurity() {
        return tlsSecurity == null ? TLSSecurityMode.STRICT : this.tlsSecurity;
    }

    public void setTLSSecurity(TLSSecurityMode value) {
        tlsSecurity = value;
    }

    public static EdgeDBConnection fromDSN(@NotNull String dsn) throws ConfigurationException, IOException {
        if (!dsn.startsWith("edgedb://")) {
            throw new ConfigurationException(String.format("DSN schema 'edgedb' expected but got '%s'", dsn.split("://")[0]));
        }

        String database = null, username = null, port = null, host = null, password = null;

        Map<String, String> args = Collections.emptyMap();

        var formattedDSN = DSN_FORMATTER.matcher(dsn).replaceAll("");

        var queryParams = DSN_QUERY_PARAMETERS.matcher(dsn);

        if (queryParams.find()) {
            args = QueryParamUtils.splitQuery(queryParams.group(1).substring(1));

            // remove args from formatted dsn
            formattedDSN = formattedDSN.replace(queryParams.group(1), "");
        }

        var partA = formattedDSN.split("/");

        if (partA.length == 2) {
            database = partA[1];
            formattedDSN = partA[0];
        }

        var partB = formattedDSN.split("@");

        if (partB.length == 2 && !partB[1].equals("")) {
            if (partB[1].contains(","))
                throw new ConfigurationException("DSN cannot contain more than one host");

            var right = partB[1].split(":");

            if(right.length == 2) {
                host = right[0];
                port = right[1];
            }
            else {
                host = right[0];
            }

            var left = partB[0].split(":");

            if(left.length == 2) {
                username = left[0];
                password = left[1];
            }
            else {
                username = left[0];
            }
        }
        else if(!formattedDSN.endsWith("@")) {
            var sub = partB[0].split(":");

            if(sub.length == 2) {
                host = sub[0];
                port = sub[1];
            }
            else if(!StringsUtil.isNullOrEmpty(sub[0])) {
                host = sub[0];
            }
        }

        var connection = new EdgeDBConnection();

        if(database != null)
            connection.setDatabase(database);

        if(host != null)
            connection.setHostname(host);

        if(username != null)
            connection.setUsername(username);

        if(password != null)
            connection.setPassword(password);

        if(port != null) {
            try{
                connection.setPort(Integer.parseInt(port));
            }
            catch (NumberFormatException e) {
                throw new ConfigurationException("port was not in the correct format", e);
            }
        }

        for (var entry : args.entrySet()) {
            var fileMatch = DSN_FILE_ARG.matcher(entry.getKey());
            var envMatch = DSN_ENV_ARG.matcher(entry.getKey());

            String value;
            String key;

            if(fileMatch.matches()){
                key = fileMatch.group(1);

                var file = new File(entry.getValue());

                if(!file.exists()) {
                    throw new FileNotFoundException(String.format("The specified argument \"%s\"'s file was not found", key));
                }

                if(!file.isFile()) {
                    throw new IllegalArgumentException(String.format("The specified argument \"%s\" is not a file", key));
                }

                if(!file.canRead()) {
                    throw new IllegalArgumentException(String.format("The specified argument \"%s\"'s file cannot be read: missing permissions", key));
                }

                value = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            }
            else if (envMatch.matches()) {
                key = entry.getKey();
                value = System.getenv(entry.getValue());

                if (value == null) {
                    throw new ConfigurationException(String.format("Environment variable \"%s\" couldn't be found", entry.getValue()));
                }
            }
            else  {
                key = entry.getKey();
                value = entry.getValue();
            }

            setArgument(connection, key, value);
        }

        return connection;
    }

    public static EdgeDBConnection fromProjectFile(Path path) throws IOException {
        return fromProjectFile(path.toFile());
    }
    public static EdgeDBConnection fromProjectFile(String path) throws IOException {
        return fromProjectFile(new File(path));
    }

    public static EdgeDBConnection fromProjectFile(File file) throws IOException {
        if(!file.exists()) {
            throw new FileNotFoundException("Couldn't find the specified project file");
        }

        file = file.getAbsoluteFile();

        var dirName = file.getParent();

        var projectDir = Paths.get(ConfigUtils.getInstanceProjectDirectory(dirName));

        if(!Files.isDirectory(projectDir)) {
            throw new FileNotFoundException(String.format("Couldn't find project directory for %s: %s", file, projectDir));
        }

        var instanceName = Files.readString(projectDir.resolve("instance-name"), StandardCharsets.UTF_8);

        return fromInstanceName(instanceName);
    }

    public static EdgeDBConnection fromInstanceName(String instanceName) throws IOException {
        var configPath = Paths.get(ConfigUtils.getCredentialsDir(), instanceName + ".json");

        if(!Files.exists(configPath))
            throw new FileNotFoundException("Config file couldn't be found at " + configPath);

        return fromJSON(Files.readString(configPath, StandardCharsets.UTF_8));
    }

    public static EdgeDBConnection resolveEdgeDBTOML() throws IOException {
        var dir = Paths.get(System.getProperty("user.dir"));

        while(true) {
            var target = dir.resolve("edgedb.toml");

            if (Files.exists(target)) {
                return fromProjectFile(target);
            }

            var parent = dir.getParent();

            if(parent == null || !Files.exists(parent)) {
                throw new FileNotFoundException("Couldn't resolve edgedb.toml file");
            }

            dir = parent;
        }
    }

    public static EdgeDBConnection parse(String connection) throws ConfigurationException, IOException {
        return parse(connection, null, true);
    }

    public static EdgeDBConnection parse(
            ConfigureFunction configure
    ) throws ConfigurationException, IOException {
        return parse(null, configure, true);
    }

    public static EdgeDBConnection parse(
            String connection,
            ConfigureFunction configure
    ) throws ConfigurationException, IOException {
        return parse(connection, configure, true);
    }

    @SuppressWarnings("SameParameterValue")
    public static EdgeDBConnection parse(
            @Nullable String connParam,
            @Nullable ConfigureFunction configure,
            boolean autoResolve
    ) throws ConfigurationException, IOException {
        var connection = new EdgeDBConnection();

        boolean isDSN = false;

        if(autoResolve) {
            try {
                connection = connection.mergeInto(resolveEdgeDBTOML());
            } catch (IOException x) {
                // ignore
            }
        }

        connection = applyEnv(connection);

        if(connParam != null) {
            if(connParam.contains("://")) {
                connection = connection.mergeInto(fromDSN(connParam));
                isDSN = true;
            } else {
                connection = connection.mergeInto(fromInstanceName(connParam));
            }
        }

        if(configure != null) {
            EdgeDBConnection clone;
            try {
                clone = (EdgeDBConnection) connection.clone();
            } catch (CloneNotSupportedException e) {
                throw new ConfigurationException("Failed to clone current connection arguments", e);
            }

            configure.accept(clone);

            if(isDSN && clone.hostname != null) {
                throw new ConfigurationException("Cannot specify DSN and 'Hostname'; they are mutually exclusive");
            }

            connection = connection.mergeInto(clone);
        }

        return connection;
    }

    private static EdgeDBConnection applyEnv(EdgeDBConnection connection) throws ConfigurationException, IOException {
        var instanceName = System.getenv(EDGEDB_INSTANCE_ENV_NAME);
        var dsn = System.getenv(EDGEDB_DSN_ENV_NAME);
        var host = System.getenv(EDGEDB_HOST_ENV_NAME);
        var port = System.getenv(EDGEDB_PORT_ENV_NAME);
        var credentials = System.getenv(EDGEDB_CREDENTIALS_FILE_ENV_NAME);
        var user = System.getenv(EDGEDB_USER_ENV_NAME);
        var pass = System.getenv(EDGEDB_PASSWORD_ENV_NAME);
        var db = System.getenv(EDGEDB_DATABASE_ENV_NAME);

        if(instanceName != null) {
            connection = connection.mergeInto(fromInstanceName(instanceName));
        }

        if(dsn != null) {
            connection = connection.mergeInto(fromDSN(dsn));
        }

        if(host != null) {
            try {
                connection.setHostname(host);
            }
            catch (ConfigurationException x) {
                if(x.getMessage().equals("DSN cannot contain more than one host")) {
                    throw new ConfigurationException("Environment variable 'EDGEDB_HOST' cannot contain more than one host", x);
                }

                throw x;
            }
        }

        if(port != null) {
            try {
                connection.setPort(Integer.parseInt(port));
            }
            catch (NumberFormatException x) {
                throw new ConfigurationException(
                        String.format(
                                "Expected integer for environment variable '%s' but got '%s'",
                                EDGEDB_PORT_ENV_NAME,
                                port
                        )
                );
            }
        }

        if(credentials != null) {
            var path = Path.of(credentials);
            if(!Files.exists(path)) {
                throw new ConfigurationException(
                        String.format(
                                "Could not find the file specified in '%s'",
                                EDGEDB_CREDENTIALS_FILE_ENV_NAME
                        )
                );
            }

            connection = connection.mergeInto(fromJSON(Files.readString(path, StandardCharsets.UTF_8)));
        }

        if(user != null) {
            connection.setUsername(user);
        }

        if(pass != null) {
            connection.setPassword(pass);
        }

        if(db != null) {
            connection.setDatabase(db);
        }

        return connection;
    }

    private EdgeDBConnection mergeInto(EdgeDBConnection other) {
        if(other.tlsSecurity == null) {
            other.tlsSecurity = this.tlsSecurity;
        }

        if(other.database == null) {
            other.database = this.database;
        }

        if(other.hostname == null) {
            other.hostname = this.hostname;
        }

        if(other.password == null) {
            other.password = this.password;
        }

        if(other.tlsca == null) {
            other.tlsca = this.tlsca;
        }

        if(other.port == null) {
            other.port = this.port;
        }

        if(other.user == null) {
            other.user = this.user;
        }

        return other;
    }

    public SSLContext getSSLContext() throws GeneralSecurityException, IOException {
        return getSSLContext("SSL");
    }
    public SSLContext getSSLContext(String instanceName) throws GeneralSecurityException, IOException {
        SSLContext sc = SSLContext.getInstance(instanceName);
        TrustManager[] trustManagers;

        if(this.tlsSecurity == TLSSecurityMode.INSECURE) {
            trustManagers = new TrustManager[] { new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                public void checkClientTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }
            }};
        }
        else {
            trustManagers = getTrustManagerFactory().getTrustManagers();
        }

        sc.init(null, trustManagers , new SecureRandom());
        return sc;
    }

    public void applyTrustManager(SslContextBuilder builder) throws GeneralSecurityException, IOException {
        if(this.tlsSecurity == TLSSecurityMode.INSECURE) {
            builder.trustManager(new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                public void checkClientTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }
            });
        }
        else {
            builder.trustManager(getTrustManagerFactory());
        }
    }

    private TrustManagerFactory getTrustManagerFactory() throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        var authority = getTLSCertificateAuthority();
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        if (StringsUtil.isNullOrEmpty(authority)) {
            // use default trust store
            trustManagerFactory.init((KeyStore)null);
            //throw new ConfigurationException("TLSCertificateAuthority cannot be null when TLSSecurity is STRICT");
        }
        else {
            var cert = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(getTLSCertificateAuthority().getBytes(StandardCharsets.US_ASCII)));

            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

            keyStore.load(null, null);
            keyStore.setCertificateEntry("server", cert);

            trustManagerFactory.init(keyStore);
        }

        return trustManagerFactory;
    }

    private static EdgeDBConnection fromJSON(String json) throws JsonProcessingException {
        return mapper.readValue(json, EdgeDBConnection.class);
    }

    private static void setArgument(EdgeDBConnection connection, String name, String value) throws ConfigurationException, IllegalArgumentException, IOException {
        if (StringsUtil.isNullOrEmpty(value))
            return;

        switch (name) {
            case "port":
                if (connection.port != null) {
                    throw new IllegalArgumentException("Port ambiguity mismatch");
                }

                try {
                    connection.setPort(Integer.parseInt(value));
                }
                catch (NumberFormatException e) {
                    throw new ConfigurationException("port was not in the correct format", e);
                }
                break;
            case "host":
                if (connection.hostname != null) {
                    throw new IllegalArgumentException("Host ambiguity mismatch");
                }

                connection.setHostname(value);
                break;
            case "user":
                if (connection.user != null) {
                    throw new IllegalArgumentException("User ambiguity mismatch");
                }

                connection.setUsername(value);
                break;
            case "password":
                if (connection.password != null) {
                    throw new IllegalArgumentException("Password ambiguity mismatch");
                }

                connection.setPassword(value);
                break;
            case "tls_cert_file":
                var file = new File(value);

                if(!file.exists()) {
                    throw new FileNotFoundException("The specified tls_cert_file file was not found");
                }

                if(!file.isFile()) {
                    throw new IllegalArgumentException("The specified tls_cert_file is not a file");
                }

                if(!file.canRead()) {
                    throw new IllegalArgumentException("The specified tls_cert_file cannot be read: missing permissions");
                }

                connection.setTLSCertificateAuthority(Files.readString(file.toPath(), StandardCharsets.UTF_8));
                break;
            case "tls_security":
                var security = EnumsUtil.searchEnum(TLSSecurityMode.class, value);

                if(security == null) {
                    throw new IllegalArgumentException(String.format("\"%s\" must be a value of edgedb.driver.TLSSecurityMode", value));
                }

                connection.setTLSSecurity(security);
                break;
            default:
                throw new IllegalArgumentException(String.format("Unexpected configuration option %s", name));
        }
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("edgedb://");

        if(this.getUsername() != null) {
            sb.append(this.getUsername());
        }

        if(this.getPassword() != null) {
            sb.append(":");
            sb.append(this.getPassword());
        }

        if(this.getHostname() != null) {
            sb.append("@");
            sb.append(this.getHostname());
            sb.append(":");
            sb.append(this.getPort());
        }

        if(this.getDatabase() != null) {
            sb.append("/");
            sb.append(this.getDatabase());
        }

        return sb.toString();
    }

    @FunctionalInterface
    public interface ConfigureFunction {
        void accept(EdgeDBConnection connection) throws ConfigurationException;
    }
}