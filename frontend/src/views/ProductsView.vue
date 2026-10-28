<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { api, notifyError } from '@/api/http'
import type { Page, Product } from '@/api/types'
import { useAuth } from '@/stores/auth'
import { fmtMoney } from '@/utils/format'

const { t } = useI18n()
const auth = useAuth()
const rows = ref<Product[]>([])
const total = ref(0)
const categories = ref<string[]>([])
const loading = ref(false)
const q = reactive({ keyword: '', category: '', status: 'ACTIVE', page: 1, size: 20 })

const dialog = ref(false)
const editing = ref<Product | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ sku: '', name: '', category: '', description: '', price: 0, cost: 0, lowStockThreshold: 10, initialStock: 0 })
const rules = computed<FormRules>(() => ({
  sku: [{ required: true, pattern: /^[A-Za-z0-9._-]{2,64}$/, message: t('products.rules.sku') }],
  name: [{ required: true, message: t('products.rules.name') }],
  category: [{ required: true, message: t('products.rules.category') }],
  price: [{ required: true, type: 'number', min: 0.01, message: t('products.rules.price') }],
}))
const statusOptions = computed(() => [
  { label: t('products.active'), value: 'ACTIVE' },
  { label: t('products.archived'), value: 'ARCHIVED' },
  { label: t('common.all'), value: '' },
])

async function load() {
  loading.value = true
  try {
    const [page, cats] = await Promise.all([
      api.get<Page<Product>>('/products', { ...q, keyword: q.keyword || undefined, category: q.category || undefined, status: q.status || undefined }),
      api.get<string[]>('/products/categories'),
    ])
    rows.value = page.items
    total.value = page.total
    categories.value = cats
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(() => [q.category, q.status, q.page, q.size], load)
let timer: number | undefined
watch(() => q.keyword, () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => { q.page = 1; load() }, 300)
})

function openCreate() {
  editing.value = null
  Object.assign(form, { sku: '', name: '', category: categories.value[0] ?? '', description: '', price: 0, cost: 0, lowStockThreshold: 10, initialStock: 0 })
  dialog.value = true
}

function openEdit(p: Product) {
  editing.value = p
  Object.assign(form, { ...p, description: p.description ?? '', initialStock: 0 })
  dialog.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editing.value) {
      const { name, category, description, price, cost, lowStockThreshold } = form
      await api.put(`/products/${editing.value.id}`, { name, category, description, price, cost, lowStockThreshold })
      ElMessage.success(t('products.updated'))
    } else {
      await api.post('/products', form)
      ElMessage.success(t('products.created'))
    }
    dialog.value = false
    load()
  } catch (e) {
    notifyError(e)
  } finally {
    saving.value = false
  }
}

async function toggle(p: Product) {
  try {
    await api.post(`/products/${p.id}/${p.status === 'ACTIVE' ? 'archive' : 'activate'}`)
    load()
  } catch (e) {
    notifyError(e)
  }
}

const margin = (p: Product) => (p.price > 0 ? Math.round(((p.price - p.cost) / p.price) * 100) : 0)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>{{ t('products.title') }}</h1>
        <p>{{ t('products.subtitle') }}</p>
      </div>
      <el-button v-if="auth.can('product:write')" type="primary" :icon="Plus" data-testid="new-product" @click="openCreate">
        {{ t('products.new') }}
      </el-button>
    </div>
    <div class="card">
      <div class="toolbar">
        <el-input v-model="q.keyword" :prefix-icon="Search" :placeholder="t('products.searchPlaceholder')" clearable style="width: 260px" />
        <el-select v-model="q.category" :placeholder="t('products.allCategories')" clearable style="width: 180px">
          <el-option v-for="c in categories" :key="c" :value="c" :label="c" />
        </el-select>
        <el-segmented v-model="q.status" :options="statusOptions" />
        <span class="spacer" />
        <span class="muted">{{ t('products.count', { n: total }) }}</span>
      </div>
      <el-table :data="rows" v-loading="loading" row-key="id">
        <el-table-column :label="t('products.colProduct')" min-width="260">
          <template #default="{ row }">
            <div class="prod">
              <div class="thumb">{{ row.category.slice(0, 2).toUpperCase() }}</div>
              <div>
                <div class="name">{{ row.name }}</div>
                <div class="muted mono">{{ row.sku }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('products.colCategory')" prop="category" width="140" />
        <el-table-column :label="t('products.colPrice')" width="110" align="right">
          <template #default="{ row }"><b class="num">{{ fmtMoney(row.price) }}</b></template>
        </el-table-column>
        <el-table-column :label="t('products.colMargin')" width="90" align="right">
          <template #default="{ row }"><span class="num muted">{{ margin(row) }}%</span></template>
        </el-table-column>
        <el-table-column :label="t('products.colStock')" width="170">
          <template #default="{ row }">
            <el-tag v-if="row.lowStock" type="warning" effect="light" round>{{ row.available }} · {{ t('products.low') }}</el-tag>
            <span v-else class="num">{{ row.available }}</span>
            <div v-if="row.reserved" class="muted small">{{ t('products.reserved', { n: row.reserved }) }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('products.colStatus')" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain" size="small">
              {{ t(`products.statusLabel.${row.status}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="auth.can('product:write')" width="150" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link @click="toggle(row)">{{ row.status === 'ACTIVE' ? t('products.archive') : t('products.restore') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total" layout="prev, pager, next" background />
      </div>
    </div>

    <el-dialog v-model="dialog" :title="editing ? t('products.editTitle', { sku: editing.sku }) : t('products.new')" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="two">
          <el-form-item :label="t('products.sku')" prop="sku"><el-input v-model="form.sku" :disabled="!!editing" data-testid="product-sku" /></el-form-item>
          <el-form-item :label="t('products.category')" prop="category">
            <el-select v-model="form.category" filterable allow-create default-first-option :placeholder="t('products.categoryPlaceholder')">
              <el-option v-for="c in categories" :key="c" :value="c" :label="c" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item :label="t('products.name')" prop="name"><el-input v-model="form.name" data-testid="product-name" /></el-form-item>
        <el-form-item :label="t('products.description')"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <div class="three">
          <el-form-item :label="t('products.price')" prop="price"><el-input-number v-model="form.price" :min="0" :precision="2" :step="1" data-testid="product-price" /></el-form-item>
          <el-form-item :label="t('products.cost')"><el-input-number v-model="form.cost" :min="0" :precision="2" /></el-form-item>
          <el-form-item :label="t('products.threshold')"><el-input-number v-model="form.lowStockThreshold" :min="0" /></el-form-item>
        </div>
        <el-form-item v-if="!editing" :label="t('products.openingStock')"><el-input-number v-model="form.initialStock" :min="0" data-testid="product-stock" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" data-testid="product-save" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.prod {
  display: flex;
  gap: 12px;
  align-items: center;
}

.thumb {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 700;
  color: var(--op-primary);
  background: linear-gradient(135deg, #eef2ff, #f5f3ff);
  flex-shrink: 0;
}

.name {
  font-weight: 500;
}

.small {
  font-size: 12px;
}

.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.three {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
</style>
