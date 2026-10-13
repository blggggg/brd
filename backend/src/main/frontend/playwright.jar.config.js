import {defineConfig} from '@playwright/test';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {randomUUID} from 'node:crypto';
const java=process.env.JAVA_HOME?join(process.env.JAVA_HOME,'bin/java.exe'):'java';
const jar=process.env.BRD_TEST_JAR||resolve('../../../target/brd-management-0.1.0.jar');
const testAdminPassword=process.env.BRD_TEST_ADMIN_PASSWORD||`test-admin-${randomUUID()}`;process.env.BRD_TEST_ADMIN_PASSWORD=testAdminPassword;
export default defineConfig({testDir:'./tests/browser',workers:1,timeout:90000,use:{baseURL:'http://127.0.0.1:8092',headless:true,channel:'msedge',viewport:{width:1440,height:1000}},webServer:{
  command:`"${java}" -jar "${jar}" --server.port=8092 --server.address=127.0.0.1 "--spring.datasource.url=jdbc:h2:mem:browser;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"`,
  url:'http://127.0.0.1:8092/api/v1/auth/csrf',env:{BRD_ADMIN_PASSWORD:testAdminPassword,BRD_STORAGE:join(tmpdir(),'brd-jar-test-'+randomUUID())},reuseExistingServer:false,timeout:60000,stdout:'ignore'
}});
