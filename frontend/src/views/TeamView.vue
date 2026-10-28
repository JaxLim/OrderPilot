<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api, notifyError } from '@/api/http'
import type { Role, User } from '@/api/types'
import { useAuth } from '@/stores/auth'
import { fmtRelative } from '@/utils/format'

const auth = useAuth()
const users = ref<User[]>([])
const loading = ref(false)
const dialog = ref(false)
const editing = ref<User | null>(null)
const form = reactive({ email: '', displayName: '', role: 'STAFF' as Role, password: '', status: 'ACTIVE' })

const roles: { value: Role; label: string; desc: string }[] = [
  { value: 'ADMIN', label: 'Admin', desc: 'Everything, including team management' },
  { value: 'MANAGER', label: 'Manager', desc: 'Catalog, inventory, orders, refunds, reports' },
  { value: 'STAFF', label: 'Staff', desc: 'Create, pay and fulfil orders' },
  { value: 'VIEWER', label: 'Viewer', desc: 'Read-only access' },
]
const roleColor: Record<Role, string> = { ADMIN: 'danger', MANAGER: 'primary', STAFF: 'success', VIEWER: 'info' }

async function load() {
  loading.value = true
  try {
    users.value = await api.get<User[]>('/users')
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

function openCreate() {
  editing.value = null
  Object.assign(form, { email: '', displayName: '', role: 'STAFF', password: '', status: 'ACTIVE' })
  dialog.value = true
}

function openEdit(u: User) {
  editing.value = u
  Object.assign(form, { email: u.email, displayName: u.displayName, role: u.role, password: '', status: u.status })
  dialog.value = true
}

async function save() {
  try {
    if (editing.value) {
      await api.put(`/users/${editing.value.id}`, { displayName: form.displayName, role: form.role, status: form.status })
    } else {
      await api.post('/users', { email: form.email, displayName: form.displayName, role: form.role, password: form.password })
    }
    ElMessage.success('Saved')
    dialog.value = false
    load()
  } catch (e) {
    notifyError(e)
  }
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>Team</h1>
        <p>Role-based access control. Permissions are enforced on the server for every endpoint.</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">Add member</el-button>
    </div>
    <div class="grid roles">
      <div v-for="r in roles" :key="r.value" class="card card-pad role">
        <el-tag :type="(roleColor[r.value] as any)" effect="light">{{ r.label }}</el-tag>
        <p class="muted">{{ r.desc }}</p>
        <b>{{ users.filter((u) => u.role === r.value).length }}</b> <span class="muted">members</span>
      </div>
    </div>
    <div class="card">
      <el-table :data="users" v-loading="loading">
        <el-table-column label="Member" min-width="240">
          <template #default="{ row }">
            <div class="name">{{ row.displayName }} <el-tag v-if="row.id === auth.me?.id" size="small" effect="plain">you</el-tag></div>
            <div class="muted">{{ row.email }}</div>
          </template>
        </el-table-column>
        <el-table-column label="Role" width="130">
          <template #default="{ row }"><el-tag :type="(roleColor[row.role as Role] as any)" effect="light">{{ row.role }}</el-tag></template>
        </el-table-column>
        <el-table-column label="Status" width="120">
          <template #default="{ row }">
            <span :class="row.status === 'ACTIVE' ? 'ok' : 'muted'">● {{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Last sign-in" width="170">
          <template #default="{ row }"><span class="muted">{{ row.lastLoginAt ? fmtRelative(row.lastLoginAt) : 'never' }}</span></template>
        </el-table-column>
        <el-table-column width="90" align="right">
          <template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">Edit</el-button></template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="editing ? 'Edit member' : 'Add member'" width="480px">
      <el-form label-position="top">
        <el-form-item label="Email"><el-input v-model="form.email" :disabled="!!editing" /></el-form-item>
        <el-form-item label="Name"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="Role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="r in roles" :key="r.value" :value="r.value" :label="`${r.label} — ${r.desc}`" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editing" label="Temporary password"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item v-else label="Status">
          <el-radio-group v-model="form.status">
            <el-radio-button value="ACTIVE">Active</el-radio-button>
            <el-radio-button value="DISABLED">Disabled</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">Cancel</el-button>
        <el-button type="primary" @click="save">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.roles {
  grid-template-columns: repeat(4, 1fr);
  margin-bottom: 16px;
}

.role p {
  font-size: 12.5px;
  margin: 8px 0;
  min-height: 34px;
}

.name {
  font-weight: 500;
}

.ok {
  color: #059669;
}
</style>
