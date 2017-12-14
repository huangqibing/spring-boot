/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.mongo;

import java.util.Arrays;
import java.util.List;

import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ReactiveMongoClientFactory}.
 *
 * @author Mark Paluch
 * @author Stephane Nicoll
 */
public class ReactiveMongoClientFactoryTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private MockEnvironment environment = new MockEnvironment();

	@Test
	public void portCanBeCustomized() {
		MongoProperties properties = new MongoProperties();
		properties.setPort(12345);
		MongoClient client = createMongoClient(properties);
		List<ServerAddress> allAddresses = extractServerAddresses(client);
		assertThat(allAddresses).hasSize(1);
		assertServerAddress(allAddresses.get(0), "localhost", 12345);
	}

	@Test
	public void hostCanBeCustomized() {
		MongoProperties properties = new MongoProperties();
		properties.setHost("mongo.example.com");
		MongoClient client = createMongoClient(properties);
		List<ServerAddress> allAddresses = extractServerAddresses(client);
		assertThat(allAddresses).hasSize(1);
		assertServerAddress(allAddresses.get(0), "mongo.example.com", 27017);
	}

	@Test
	public void credentialsCanBeCustomized() {
		MongoProperties properties = new MongoProperties();
		properties.setUsername("user");
		properties.setPassword("secret".toCharArray());
		MongoClient client = createMongoClient(properties);
		assertMongoCredential(extractMongoCredentials(client).get(0), "user", "secret",
				"test");
	}

	@Test
	public void databaseCanBeCustomized() {
		MongoProperties properties = new MongoProperties();
		properties.setDatabase("foo");
		properties.setUsername("user");
		properties.setPassword("secret".toCharArray());
		MongoClient client = createMongoClient(properties);
		assertMongoCredential(extractMongoCredentials(client).get(0), "user", "secret",
				"foo");
	}

	@Test
	public void authenticationDatabaseCanBeCustomized() {
		MongoProperties properties = new MongoProperties();
		properties.setAuthenticationDatabase("foo");
		properties.setUsername("user");
		properties.setPassword("secret".toCharArray());
		MongoClient client = createMongoClient(properties);
		assertMongoCredential(extractMongoCredentials(client).get(0), "user", "secret",
				"foo");
	}

	@Test
	public void uriCanBeCustomized() {
		MongoProperties properties = new MongoProperties();
		properties.setUri("mongodb://user:secret@mongo1.example.com:12345,"
				+ "mongo2.example.com:23456/test");
		MongoClient client = createMongoClient(properties);
		List<ServerAddress> allAddresses = extractServerAddresses(client);
		assertThat(allAddresses).hasSize(2);
		assertServerAddress(allAddresses.get(0), "mongo1.example.com", 12345);
		assertServerAddress(allAddresses.get(1), "mongo2.example.com", 23456);
		List<MongoCredential> credentialsList = extractMongoCredentials(client);
		assertThat(credentialsList).hasSize(1);
		assertMongoCredential(credentialsList.get(0), "user", "secret", "test");
	}

	@Test
	public void uriCannotBeSetWithCredentials() {
		MongoProperties properties = new MongoProperties();
		properties.setUri("mongodb://127.0.0.1:1234/mydb");
		properties.setUsername("user");
		properties.setPassword("secret".toCharArray());
		this.thrown.expect(IllegalStateException.class);
		this.thrown.expectMessage("Invalid mongo configuration, "
				+ "either uri or host/port/credentials must be specified");
		createMongoClient(properties);
	}

	@Test
	public void uriCannotBeSetWithHostPort() {
		MongoProperties properties = new MongoProperties();
		properties.setUri("mongodb://127.0.0.1:1234/mydb");
		properties.setHost("localhost");
		properties.setPort(4567);
		this.thrown.expect(IllegalStateException.class);
		this.thrown.expectMessage("Invalid mongo configuration, "
				+ "either uri or host/port/credentials must be specified");
		createMongoClient(properties);
	}

	@Test
	public void uriIsIgnoredInEmbeddedMode() {
		MongoProperties properties = new MongoProperties();
		properties.setUri("mongodb://mongo.example.com:1234/mydb");
		this.environment.setProperty("local.mongo.port", "4000");
		MongoClient client = createMongoClient(properties, this.environment);
		List<ServerAddress> allAddresses = extractServerAddresses(client);
		assertThat(allAddresses).hasSize(1);
		assertServerAddress(allAddresses.get(0), "localhost", 4000);
	}

	@Test
	public void customizerIsInvoked() {
		MongoProperties properties = new MongoProperties();
		MongoClientSettingsBuilderCustomizer customizer = mock(
				MongoClientSettingsBuilderCustomizer.class);
		createMongoClient(properties, this.environment, customizer);
		verify(customizer).customize(any(MongoClientSettings.Builder.class));
	}

	@Test
	public void customizerIsInvokedForEmbeddedMongo() {
		MongoProperties properties = new MongoProperties();
		this.environment.setProperty("local.mongo.port", "27017");
		MongoClientSettingsBuilderCustomizer customizer = mock(
				MongoClientSettingsBuilderCustomizer.class);
		createMongoClient(properties, this.environment, customizer);
		verify(customizer).customize(any(MongoClientSettings.Builder.class));
	}

	private MongoClient createMongoClient(MongoProperties properties) {
		return createMongoClient(properties, this.environment);
	}

	private MongoClient createMongoClient(MongoProperties properties,
			Environment environment,
			MongoClientSettingsBuilderCustomizer... customizers) {
		return new ReactiveMongoClientFactory(properties, environment,
				Arrays.asList(customizers)).createMongoClient(null);
	}

	private List<ServerAddress> extractServerAddresses(MongoClient client) {
		MongoClientSettings settings = client.getSettings();
		ClusterSettings clusterSettings = settings.getClusterSettings();
		return clusterSettings.getHosts();
	}

	private List<MongoCredential> extractMongoCredentials(MongoClient client) {
		MongoClientSettings settings = client.getSettings();
		return settings.getCredentialList();
	}

	private void assertServerAddress(ServerAddress serverAddress, String expectedHost,
			int expectedPort) {
		assertThat(serverAddress.getHost()).isEqualTo(expectedHost);
		assertThat(serverAddress.getPort()).isEqualTo(expectedPort);
	}

	private void assertMongoCredential(MongoCredential credentials,
			String expectedUsername, String expectedPassword, String expectedSource) {
		assertThat(credentials.getUserName()).isEqualTo(expectedUsername);
		assertThat(credentials.getPassword()).isEqualTo(expectedPassword.toCharArray());
		assertThat(credentials.getSource()).isEqualTo(expectedSource);
	}

}
