import type { Product } from '@/features/products/types'

/** Simple generated product "photos" so the mock shop needs no image files. */
function placeholderImage(
  background: string,
  foreground: string,
  shape: string,
) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 600"><rect width="600" height="600" fill="${background}"/><g fill="${foreground}">${shape}</g></svg>`
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}

const shapes = {
  mug: '<rect x="200" y="210" width="170" height="200" rx="24"/><path d="M370 260h30a45 45 0 0 1 0 90h-30v-26h30a19 19 0 0 0 0-38h-30z"/>',
  bowl: '<path d="M160 290h280a140 140 0 0 1-280 0z"/>',
  candle:
    '<rect x="250" y="250" width="100" height="180" rx="12"/><path d="M300 170c22 30 22 55 0 70-22-15-22-40 0-70z"/>',
  notebook:
    '<rect x="200" y="160" width="200" height="280" rx="14"/><rect x="225" y="160" width="10" height="280" fill-opacity=".25" fill="#000"/>',
  pen: '<rect x="285" y="150" width="30" height="260" rx="10"/><path d="M285 410h30l-15 45z"/>',
  bottle:
    '<rect x="255" y="150" width="90" height="40" rx="10"/><rect x="230" y="190" width="140" height="260" rx="40"/>',
  blanket:
    '<rect x="170" y="210" width="260" height="180" rx="20"/><path d="M170 260h260M170 340h260" stroke="#fff" stroke-opacity=".4" stroke-width="14"/>',
  plant:
    '<path d="M240 350h120l-15 100h-90z"/><path d="M300 350c-10-80-70-110-90-100 0 50 40 90 90 100zM300 350c10-90 70-120 95-110 0 55-45 100-95 110z"/>',
  lamp: '<path d="M230 260l40-100h60l40 100z"/><rect x="292" y="260" width="16" height="150"/><rect x="240" y="410" width="120" height="20" rx="8"/>',
  backpack:
    '<rect x="210" y="190" width="180" height="250" rx="50"/><rect x="245" y="300" width="110" height="90" rx="16" fill-opacity=".3" fill="#000"/><rect x="270" y="160" width="60" height="40" rx="20"/>',
}

type Seed = Omit<Product, 'imageUrl'> & {
  colors: [string, string]
  shape: keyof typeof shapes
}

const seeds: Seed[] = [
  {
    id: 'p1',
    slug: 'stoneware-mug',
    name: 'Stoneware Mug',
    category: 'Kitchen',
    description:
      'A hand-glazed 350 ml stoneware mug with a comfortable handle. Dishwasher and microwave safe.',
    price: { amount: 1800, currency: 'EUR' },
    imageAlt: 'Sage green stoneware mug',
    stock: 42,
    colors: ['#e7ece4', '#7f9a7a'],
    shape: 'mug',
  },
  {
    id: 'p2',
    slug: 'serving-bowl',
    name: 'Serving Bowl',
    category: 'Kitchen',
    description: 'A wide ceramic bowl for salads and pasta, 26 cm across.',
    price: { amount: 3400, currency: 'EUR' },
    imageAlt: 'Wide off-white ceramic serving bowl',
    stock: 3,
    colors: ['#efe9df', '#c8b79c'],
    shape: 'bowl',
  },
  {
    id: 'p3',
    slug: 'soy-candle',
    name: 'Soy Candle — Cedar',
    category: 'Home',
    description:
      'Hand-poured soy wax candle with a cedar and bergamot scent. Burns for about 45 hours.',
    price: { amount: 2200, currency: 'EUR' },
    imageAlt: 'Amber soy candle with a lit flame',
    stock: 18,
    colors: ['#f3e7d8', '#b8763d'],
    shape: 'candle',
  },
  {
    id: 'p4',
    slug: 'linen-notebook',
    name: 'Linen Notebook',
    category: 'Stationery',
    description:
      'A5 notebook with a linen cover and 192 pages of dotted, fountain-pen-friendly paper.',
    price: { amount: 1450, currency: 'EUR' },
    imageAlt: 'Navy linen-covered notebook',
    stock: 60,
    colors: ['#e4e8ef', '#34496e'],
    shape: 'notebook',
  },
  {
    id: 'p5',
    slug: 'brass-pen',
    name: 'Brass Pen',
    category: 'Stationery',
    description:
      'A solid brass ballpoint pen that develops a warm patina over time. Refillable.',
    price: { amount: 3900, currency: 'EUR' },
    imageAlt: 'Brass ballpoint pen',
    stock: 0,
    colors: ['#f4efdf', '#b8973a'],
    shape: 'pen',
  },
  {
    id: 'p6',
    slug: 'steel-water-bottle',
    name: 'Steel Water Bottle',
    category: 'Outdoors',
    description:
      'Double-walled 750 ml bottle that keeps drinks cold for 24 hours or hot for 12.',
    price: { amount: 2900, currency: 'EUR' },
    imageAlt: 'Terracotta-coloured steel water bottle',
    stock: 25,
    colors: ['#f2e3dc', '#b9583b'],
    shape: 'bottle',
  },
  {
    id: 'p7',
    slug: 'wool-throw',
    name: 'Wool Throw',
    category: 'Home',
    description: 'A soft merino wool throw, 130 × 180 cm, woven in Estonia.',
    price: { amount: 8900, currency: 'EUR' },
    imageAlt: 'Folded grey wool throw blanket',
    stock: 7,
    colors: ['#ebeae8', '#77736d'],
    shape: 'blanket',
  },
  {
    id: 'p8',
    slug: 'potted-plant',
    name: 'Potted Pilea',
    category: 'Home',
    description: 'An easy-care Pilea peperomioides in a 12 cm terracotta pot.',
    price: { amount: 2600, currency: 'EUR' },
    imageAlt: 'Small green plant in a terracotta pot',
    stock: 12,
    colors: ['#e5efe4', '#3f7a4a'],
    shape: 'plant',
  },
  {
    id: 'p9',
    slug: 'desk-lamp',
    name: 'Desk Lamp',
    category: 'Home',
    description:
      'A minimalist steel desk lamp with a warm 2700 K LED bulb included.',
    price: { amount: 6900, currency: 'EUR' },
    imageAlt: 'Black steel desk lamp',
    stock: 9,
    colors: ['#ecebe7', '#2f2e2b'],
    shape: 'lamp',
  },
  {
    id: 'p10',
    slug: 'canvas-backpack',
    name: 'Canvas Backpack',
    category: 'Outdoors',
    description:
      'Waxed canvas backpack with a padded 15" laptop sleeve and leather straps.',
    price: { amount: 11900, currency: 'EUR' },
    imageAlt: 'Olive waxed canvas backpack',
    stock: 4,
    colors: ['#eceadf', '#6b6a3c'],
    shape: 'backpack',
  },
  {
    id: 'p11',
    slug: 'espresso-cups',
    name: 'Espresso Cups (set of 2)',
    category: 'Kitchen',
    description: 'Two 90 ml porcelain espresso cups with a matte outer finish.',
    price: { amount: 2400, currency: 'EUR' },
    imageAlt: 'Two small white espresso cups',
    stock: 30,
    colors: ['#eef0f2', '#9aa6b2'],
    shape: 'mug',
  },
  {
    id: 'p12',
    slug: 'pocket-notebook',
    name: 'Pocket Notebooks (3-pack)',
    category: 'Stationery',
    description:
      'Three 9 × 14 cm notebooks with kraft covers. Blank, lined and dotted.',
    price: { amount: 990, currency: 'EUR' },
    imageAlt: 'Three small kraft-paper notebooks',
    stock: 80,
    colors: ['#f3ece2', '#a07a52'],
    shape: 'notebook',
  },
]

export const products: Product[] = seeds.map(
  ({ colors, shape, ...product }) => ({
    ...product,
    imageUrl: placeholderImage(colors[0], colors[1], shapes[shape]),
  }),
)
