<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api, newIdempotencyKey, notifyError } from '@/api/http'
import type { OrderDetail, Page, Product } from '@/api/types'
import { fmtMoney } from '@/utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ created: [OrderDetail] }>()

const products = ref<Product[]>([])
const saving = ref(false)
// One key per dialog session: double clicks and network retries can never create two orders.
let idempotencyKey = newIdempotencyKey()

const form = reactive({
  customerName: '',
  customerEmail: '',
  shippingAddress: '',
  remark: '',
  lines: [{ productId: undefined as number | undefined, quantity: 1 }],
})

watch(visible, async (open) => {
  if (!open) return
  idempotencyKey = newIdempotencyKey()
  Object.assign(form, { customerName: '', customerEmail: '', shippingAddress: '', remark: '' })
  form.lines = [{ productId: undefined, quantity: 1 }]
  try {
    const page = await api.get<Page<Product>>('/products', { status: 'ACTIVE', size: 100 })
    products.value = page.items
  } catch (e) {
    notifyError(e)
  }
})

const byId = computed(() => new Map(products.value.map((p) => [p.id, p])))
const total = computed(() =>
  form.lines.reduce((sum, l) => sum + (l.productId ? (byId.value.get(l.productId)?.price ?? 0) * l.quantity : 0), 0),
)
const valid = computed(() => form.customerName.trim() && form.lines.some((l) => l.productId))

async function submit() {
  saving.value = true
  try {
    const detail = await api.post<OrderDetail>('/orders', {
      customerName: form.customerName,
      customerEmail: form.customerEmail || undefined,
      shippingAddress: form.shippingAddress || undefined,
      remark: form.remark || undefined,
      items: form.lines.filter((l) => l.productId).map((l) => ({ productId: l.productId, quantity: l.quantity })),
    }, { 'Idempotency-Key': idempotencyKey })
    ElMessage.success(t('orderForm.created', { no: detail.order.orderNo }))
    visible.value = false
    emit('created', detail)
  } catch (e) {
    notifyError(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="t('orderForm.title')" width="720px" destroy-on-close>
    <el-form label-position="top" data-testid="order-form">
      <div class="two">
        <el-form-item :label="t('orderForm.customerName')" required>
          <el-input v-model="form.customerName" data-testid="order-customer" />
        </el-form-item>
        <el-form-item :label="t('orderForm.customerEmail')">
          <el-input v-model="form.customerEmail" placeholder="olivia@example.com" />
        </el-form-item>
      </div>
      <el-form-item :label="t('orderForm.shippingAddress')">
        <el-input v-model="form.shippingAddress" :placeholder="t('orderForm.addressPlaceholder')" />
      </el-form-item>

      <div class="lines-head">{{ t('orderForm.items') }}</div>
      <div v-for="(line, i) in form.lines" :key="i" class="line">
        <el-select v-model="line.productId" filterable :placeholder="t('orderForm.productPlaceholder')" class="prod"
                   :data-testid="`order-line-product-${i}`">
          <el-option v-for="p in products" :key="p.id" :value="p.id" :label="`${p.name} (${p.sku})`"
                     :disabled="p.available === 0">
            <div class="opt">
              <span>{{ p.name }} <span class="muted mono">{{ p.sku }}</span></span>
              <span :class="p.lowStock ? 'warn' : 'muted'">{{ t('orderForm.inStock', { n: p.available }) }} · {{ fmtMoney(p.price) }}</span>
            </div>
          </el-option>
        </el-select>
        <el-input-number v-model="line.quantity" :min="1"
                         :max="line.productId ? Math.max(1, byId.get(line.productId)?.available ?? 1) : 999" />
        <span class="line-total num">
          {{ line.productId ? fmtMoney((byId.get(line.productId)?.price ?? 0) * line.quantity) : '—' }}
        </span>
        <el-button :icon="Delete" text :disabled="form.lines.length === 1" @click="form.lines.splice(i, 1)" />
      </div>
      <el-button :icon="Plus" text type="primary" @click="form.lines.push({ productId: undefined, quantity: 1 })">
        {{ t('orderForm.addItem') }}
      </el-button>
      <el-form-item :label="t('orderForm.note')" class="mt">
        <el-input v-model="form.remark" :placeholder="t('common.optional')" />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="foot">
        <div>
          <span class="muted">{{ t('common.total') }}</span>
          <b class="total num">{{ fmtMoney(total) }}</b>
          <span class="muted tiny">{{ t('orderForm.serverPrices') }}</span>
        </div>
        <div>
          <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" :disabled="!valid" data-testid="order-submit" @click="submit">
            {{ t('orderForm.create') }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.lines-head {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 8px;
}

.line {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}

.prod {
  flex: 1;
}

.line-total {
  width: 90px;
  text-align: right;
  font-weight: 600;
}

.opt {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}

.warn {
  color: #ea580c;
}

.mt {
  margin-top: 12px;
}

.foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.total {
  font-size: 20px;
  margin: 0 10px;
}

.tiny {
  font-size: 12px;
}
</style>
