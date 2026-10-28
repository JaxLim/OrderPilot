<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { api, notifyError } from '@/api/http'
import type { Role, User } from '@/api/types'
import { useAuth } from '@/stores/auth'
import { fmtRelative } from '@/utils/format'

const { t } = useI18n()
const auth = useAuth()
const users = ref<User[]>([])
const loading = ref(false)
const dialog = ref(false)
const editing = ref<User | null>(null)
const form = reactive({ email: '', displayName: '', role: 'STAFF' as Role, password: '', status: 'ACTIVE' })

const roles: Role[] = ['ADMIN', 'MANAGER', 'STAFF', 'VIEWER']
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
    ElMessage.success(t('common.saved'))
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
        <h1>{{ t('team.title') }}</h1>
        <p>{{ t('team.subtitle') }}</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('team.add') }}</el-button>
    </div>
    <div class="grid roles">
      <div v-for="r in roles" :key="r" class="card card-pad role">
        <el-tag :type="(roleColor[r] as any)" effect="light">{{ t(`roles.${r}`) }}</el-tag>
        <p class="muted">{{ t(`roles.desc.${r}`) }}</p>
        <b>{{ users.filter((u) => u.role === r).length }}</b> <span class="muted">{{ t('team.members') }}</span>
      </div>
    </div>
    <div class="card">
      <el-table :data="users" v-loading="loading">
        <el-table-column :label="t('team.colMember')" min-width="240">
          <template #default="{ row }">
            <div class="name">{{ row.displayName }} <el-tag v-if="row.id === auth.me?.id" size="small" effect="plain">{{ t('common.you') }}</el-tag></div>
            <div class="muted">{{ row.email }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('team.colRole')" width="130">
          <template #default="{ row }"><el-tag :type="(roleColor[row.role as Role] as any)" effect="light">{{ t(`roles.${row.role}`) }}</el-tag></template>
        </el-table-column>
        <el-table-column :label="t('team.colStatus')" width="120">
          <template #default="{ row }">
            <span :class="row.status === 'ACTIVE' ? 'ok' : 'muted'">● {{ row.status === 'ACTIVE' ? t('team.active') : t('team.disabled') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('team.colLastLogin')" width="170">
          <template #default="{ row }"><span class="muted">{{ row.lastLoginAt ? fmtRelative(row.lastLoginAt) : t('common.never') }}</span></template>
        </el-table-column>
        <el-table-column width="90" align="right">
          <template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button></template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="editing ? t('team.editTitle') : t('team.addTitle')" width="480px">
      <el-form label-position="top">
        <el-form-item :label="t('team.email')"><el-input v-model="form.email" :disabled="!!editing" /></el-form-item>
        <el-form-item :label="t('team.name')"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item :label="t('team.role')">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="r in roles" :key="r" :value="r" :label="`${t(`roles.${r}`)} — ${t(`roles.desc.${r}`)}`" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editing" :label="t('team.tempPassword')"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item v-else :label="t('team.status')">
          <el-radio-group v-model="form.status">
            <el-radio-button value="ACTIVE">{{ t('team.active') }}</el-radio-button>
            <el-radio-button value="DISABLED">{{ t('team.disabled') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ t('common.save') }}</el-button>
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
