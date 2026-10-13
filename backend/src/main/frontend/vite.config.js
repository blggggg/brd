export default {server:{host:'127.0.0.1',port:5173,strictPort:true,proxy:{'/api':process.env.BRD_API_TARGET||'http://127.0.0.1:8091'}},build:{outDir:'dist'}};
