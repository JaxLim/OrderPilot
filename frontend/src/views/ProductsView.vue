<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { api, notifyError } from '@/api/http'
import type { Page, Product } from '@/api/types'
import { useAuth } from '@/stores/auth'
import { fmtMoney } from '@/utils/format'

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
const rules: FormRules = {
  sku: [{ required: true, pattern: /^[A-Za-z0-9._-]{2,64}$/, message: 'Letters, digits, . _ -' }],
  name: [{ required: true, message: 'Name is required' }],
  category: [{ required: true, message: 'Category is required' }],
  price: [{ required: true, type: 'number', min: 0.01, message: 'Price must be > 0' }],
}

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
let t: number | undefined
watch(() => q.keyword, () => {
  window.clearTimeout(t)
  t = window.setTimeout(() => { q.page = 1; load() }, 300)
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
      ElMessage.success('Product updated — cache invalidated after commit')
    } else {
      await api.post('/products', form)
      ElMessage.success('Product created')
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
        <h1>Products</h1>
        <p>Catalog data is served through a Redis cache-aside layer; stock is always live.</p>
      </div>
      <el-button v-if="auth.can('product:write')" type="primary" :icon="Plus" data-testid="new-product" @click="openCreate">
        New product
      </el-button>
    </div>
    <div class="card">
      <div class="toolbar">
        <el-input v-model="q.keyword" :prefix-icon="Search" placeholder="Search name or SKU" clearable style="width: 260px" />
        <el-select v-model="q.category" placeholder="All categories" clearable style="width: 180px">
          <el-option v-for="c in categories" :key="c" :value="c" :label="c" />
        </el-select>
        <el-segmented v-model="q.status" :options="[{ label: 'Active', value: 'ACTIVE' }, { label: 'Archived', value: 'ARCHIVED' }, { label: 'All', value: '' }]" />
        <span class="spacer" />
        <span class="muted">{{ total }} products</span>
      </div>
      <el-table :data="rows" v-loading="loading" row-key="id">
        <el-table-column label="Product" min-width="260">
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
        <el-table-column label="Category" prop="category" width="140" />
        <el-table-column label="Price" width="110" align="right">
          <template #default="{ row }"><b class="num">{{ fmtMoney(row.price) }}</b></template>
        </el-table-column>
        <el-table-column label="Margin" width="90" align="right">
          <template #default="{ row }"><span class="num muted">{{ margin(row) }}%</span></template>
        </el-table-column>
        <el-table-column label="Stock" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.lowStock" type="warning" effect="light" round>{{ row.available }} · low</el-tag>
            <span v-else class="num">{{ row.available }}</span>
            <span v-if="row.reserved" class="muted small"> +{{ row.reserved }} reserved</span>
          </template>
        </el-table-column>
        <el-table-column label="Status" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="auth.can('product:write')" width="150" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">Edit</el-button>
            <el-button link @click="toggle(row)">{{ row.status === 'ACTIVE' ? 'Archive' : 'Restore' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total" layout="prev, pager, next" background />
      </div>
    </div>

    <el-dialog v-model="dialog" :title="editing ? `Edit ${editing.sku}` : 'New product'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="two">
          <el-form-item label="SKU" prop="sku"><el-input v-model="form.sku" :disabled="!!editing" data-testid="product-sku" /></el-form-item>
          <el-form-item label="Category" prop="category">
            <el-select v-model="form.category" filterable allow-create default-first-option placeholder="Pick or type">
              <el-option v-for="c in categories" :key="c" :value="c" :label="c" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="Name" prop="name"><el-input v-model="form.name" data-testid="product-name" /></el-form-item>
        <el-form-item label="Description"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <div class="three">
          <el-form-item label="Price (USD)" prop="price"><el-input-number v-model="form.price" :min="0" :precision="2" :step="1" data-testid="product-price" /></el-form-item>
          <el-form-item label="Unit cost"><el-input-number v-model="form.cost" :min="0" :precision="2" /></el-form-item>
          <el-form-item label="Low-stock alert at"><el-input-number v-model="form.lowStockThreshold" :min="0" /></el-form-item>
        </div>
        <el-form-item v-if="!editing" label="Opening stock"><el-input-number v-model="form.initialStock" :min="0" data-testid="product-stock" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" data-testid="product-save" @click="save">Save</el-button>
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
