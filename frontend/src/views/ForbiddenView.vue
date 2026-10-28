<template>
  <div class="page">
    <el-result icon="warning" title="You don't have access to this page"
               sub-title="Ask a workspace admin to change your role if you need it.">
      <template #extra>
        <router-link to="/orders"><el-button type="primary">Go to orders</el-button></router-link>
      </template>
    </el-result>
  </div>
</template>
