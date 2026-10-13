import {defineConfig} from '@playwright/test';
import jarConfig from './playwright.jar.config.js';
// A non-localhost origin reproduces real HTTP browser restrictions without
// contacting a public server or modifying the operating system hosts file.
export default defineConfig({...jarConfig,use:{...jarConfig.use,
  baseURL:'http://brd-http.test:8092',
  launchOptions:{args:['--host-resolver-rules=MAP brd-http.test 127.0.0.1','--no-proxy-server']}
}});
