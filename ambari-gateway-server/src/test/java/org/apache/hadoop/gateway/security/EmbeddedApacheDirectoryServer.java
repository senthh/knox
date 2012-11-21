/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.security;

import com.google.common.io.Files;
import org.apache.hadoop.test.category.ManualTests;
import org.apache.hadoop.test.category.MediumTests;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.net.URL;

@Category( { ManualTests.class, MediumTests.class } )
public class EmbeddedApacheDirectoryServer {

  private DefaultDirectoryService directory;

  private LdapServer transport;

  private Partition partition;

  public static void main( String[] args ) throws Exception {
    EmbeddedApacheDirectoryServer ldap;
    ldap = new EmbeddedApacheDirectoryServer( "dc=ambari,dc=apache,dc=org", null, 33389 );
    ldap.start();
    ldap.loadLdif( ClassLoader.getSystemResource( "users.ldif" ) );
  }

  public EmbeddedApacheDirectoryServer( String rootDn, File workDir, int ldapPort ) throws Exception {
    partition = createRootParition( rootDn );
    directory = createDirectory( partition, workDir );
    transport = createTransport( directory, ldapPort );
  }

  private static Partition createRootParition( String dn ) {
    JdbmPartition partition = new JdbmPartition();
    partition.setId( "root" );
    partition.setSuffix( dn );
    return partition;
  }

  private static DefaultDirectoryService createDirectory( Partition rootPartition, File workDir ) throws Exception {
    DefaultDirectoryService directory = new DefaultDirectoryService();
    directory.addPartition( rootPartition );
    directory.setExitVmOnShutdown( false );
    directory.setShutdownHookEnabled( true );
    directory.getChangeLog().setEnabled( false );
    directory.setDenormalizeOpAttrsEnabled( true );
    directory.setWorkingDirectory( initWorkDir( workDir ) );
    return directory;
  }

  private static LdapServer createTransport( DirectoryService directory, int ldapPort ) {
    LdapServer transport = new LdapServer();
    transport.setDirectoryService( directory );
    transport.setTransports( new TcpTransport( initLdapPort( ldapPort ) ) );
    return transport;
  }

  private static File initWorkDir( File workDir ) {
    File dir = workDir;
    if( dir == null ) {
      dir = new File( System.getProperty( "user.dir" ), EmbeddedApacheDirectoryServer.class.getName() );
    }
    if( dir.exists() ) {
      dir = Files.createTempDir();
    }
    return dir;
  }

  private static final int initLdapPort( int ldapPort ) {
    return ( ldapPort <= 0 ) ? 10389 : ldapPort;
  }

  public void start() throws Exception {
    directory.startup();
    transport.start();

    LdapDN dn = partition.getSuffixDn();
    String dc = dn.getUpName().split("[=,]")[1];
    ServerEntry entry = directory.newEntry( dn );
    entry.add( "objectClass", "top", "domain", "extensibleObject" );
    entry.add( "dc", dc );
    directory.getAdminSession().add( entry );
  }

  public void stop() throws Exception {
    try {
      transport.stop();
      directory.shutdown();
    } finally {
      deleteDir( directory.getWorkingDirectory() );
    }
  }

  public void loadLdif( URL url ) {
    LdifFileLoader loader = new LdifFileLoader( directory.getAdminSession(), url.toExternalForm() );
    loader.execute();
  }

  private static boolean deleteDir( File dir ) {
    if( dir.isDirectory() ) {
      String[] children = dir.list();
      for( String child : children ) {
        boolean success = deleteDir( new File( dir, child ) );
        if( !success ) {
          return false;
        }
      }
    }
    return dir.delete();
  }

}
