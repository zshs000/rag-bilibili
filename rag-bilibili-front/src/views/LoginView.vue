<template>
  <div class="landing-page">
    <section class="surface landing-hero">
      <div class="landing-copy">
        <div class="landing-brand-chip">
          <div class="landing-brand-mark">RB</div>
          <div>
            <strong>RAG Bilibili</strong>
            <p>把看过的视频留成随时能再问的资料</p>
          </div>
        </div>

        <div class="eyebrow">VIDEO KNOWLEDGE WORKSPACE</div>
        <h1 class="landing-title">把看过的视频，留下来随时再问。</h1>
        <p class="landing-summary">{{ activeScene.summary }}</p>

        <div class="landing-scene-switch" role="tablist" aria-label="使用场景切换">
          <button
            v-for="scene in scenes"
            :key="scene.key"
            type="button"
            class="landing-scene-chip"
            :class="{ active: scene.key === activeSceneKey }"
            :aria-selected="scene.key === activeSceneKey"
            @click="switchScene(scene.key)"
          >
            <strong>{{ scene.label }}</strong>
            <span>{{ scene.short }}</span>
          </button>
        </div>

        <div class="landing-cta">
          <a class="landing-anchor-button landing-anchor-button-primary" href="#login-card">进入工作台</a>
          <a class="landing-anchor-button landing-anchor-button-secondary" href="#questions">先看示例问题</a>
        </div>
      </div>

      <div class="landing-visual" aria-hidden="true">
        <div class="landing-orb landing-orb-lg"></div>
        <div class="landing-orb landing-orb-sm"></div>
        <div class="landing-grid-lines"></div>

        <div class="surface-strong landing-float-card landing-float-card-top">
          <span>{{ activeScene.badge }}</span>
          <strong>{{ activeScene.floatTitle }}</strong>
        </div>

        <div class="surface landing-device">
          <div class="landing-device-topbar">
            <span></span>
            <span></span>
            <span></span>
          </div>

          <div class="landing-device-search">{{ activeScene.search }}</div>

          <div class="landing-device-layout">
            <div class="landing-device-pane">
              <div class="landing-device-label">现在适合做什么</div>
              <div
                v-for="focus in activeScene.focusCards"
                :key="focus.title"
                class="landing-device-card"
                :class="{ 'muted-card': focus.muted }"
              >
                <strong>{{ focus.title }}</strong>
                <p>{{ focus.text }}</p>
              </div>
            </div>

            <div class="landing-device-pane landing-device-pane-wide">
              <div class="landing-device-label">问题预览</div>
              <div class="landing-chat-row landing-chat-row-user">{{ activeExample.prompt }}</div>
              <div class="landing-chat-row landing-chat-row-assistant">{{ activeExample.answer }}</div>
              <div class="landing-preview-tags">
                <span v-for="tag in activeScene.tags" :key="tag">{{ tag }}</span>
              </div>
            </div>
          </div>
        </div>

      </div>
    </section>

    <section class="landing-content-grid">
      <div class="landing-stack">
        <section class="surface card-section landing-section">
          <div class="landing-section-header">
            <div>
              <div class="eyebrow">Choose First</div>
              <h2>你现在更想先做哪件事？</h2>
            </div>
          </div>

          <div class="landing-feature-grid">
            <button
              v-for="scene in scenes"
              :key="scene.key"
              type="button"
              class="surface-strong landing-feature-card landing-feature-button"
              :class="{ active: scene.key === activeSceneKey }"
              @click="switchScene(scene.key)"
            >
              <div class="landing-feature-index">{{ scene.index }}</div>
              <strong>{{ scene.label }}</strong>
              <p>{{ scene.cardText }}</p>
            </button>
          </div>
        </section>

        <section id="questions" class="surface card-section landing-section">
          <div class="landing-section-header">
            <div>
              <div class="eyebrow">Try Questions</div>
              <h2>{{ activeScene.questionTitle }}</h2>
            </div>
          </div>

          <div class="landing-example-grid">
            <button
              v-for="(example, index) in activeScene.examples"
              :key="example.prompt"
              type="button"
              class="surface-strong landing-example-card"
              :class="{ active: index === activeExampleIndex }"
              @click="activeExampleIndex = index"
            >
              <strong>{{ example.title }}</strong>
              <p>{{ example.prompt }}</p>
            </button>
          </div>
        </section>

        <section class="surface card-section landing-section">
          <div class="landing-section-header">
            <div>
              <div class="eyebrow">Start Smoothly</div>
              <h2>第一次用，照着这三步走就够了。</h2>
            </div>
          </div>

          <div class="landing-step-grid">
            <article class="surface-strong landing-step-card">
              <div class="landing-step-number">1</div>
              <strong>先导入一条熟悉的视频</strong>
              <p>你越熟悉原视频内容，越容易判断整理结果是不是你想要的。</p>
            </article>
            <article class="surface-strong landing-step-card">
              <div class="landing-step-number">2</div>
              <strong>先问一个具体问题</strong>
              <p>别一上来就问得太大，从一个主题、一个观点或一个片段开始更容易得到好结果。</p>
            </article>
            <article class="surface-strong landing-step-card">
              <div class="landing-step-number">3</div>
              <strong>顺着结果继续追问</strong>
              <p>拿到初步答案后，再问例子、适用场景和关键差异，内容会越问越清楚。</p>
            </article>
          </div>
        </section>
      </div>

      <aside id="login-card" class="surface auth-panel landing-login-panel">
        <div class="eyebrow">Workspace Access</div>
        <h2>登录工作台</h2>
        <p class="landing-login-note">登录后就可以开始导入视频、查看整理结果，并围绕内容继续提问。</p>

        <div class="surface-strong landing-login-tip">
          <strong>{{ activeScene.loginTitle }}</strong>
          <p>{{ activeScene.loginTip }}</p>
        </div>

        <el-alert v-if="inlineError" class="alert-inline" type="error" :title="inlineError" show-icon />

        <el-form :model="form" label-position="top" @submit.prevent="handleSubmit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model.trim="form.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model.trim="form.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <div class="toolbar">
            <el-button type="primary" :loading="authStore.loading" @click="handleSubmit">登录</el-button>
            <RouterLink to="/register">
              <el-button>创建账号</el-button>
            </RouterLink>
          </div>
        </el-form>

        <div class="surface-strong landing-login-checklist">
          <div v-for="item in activeScene.benefits" :key="item.title" class="landing-login-check-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.text }}</p>
          </div>
        </div>

        <div v-if="authStore.canUseDeveloperEntry" class="surface-strong auth-dev-entry">
          <div class="eyebrow">Developer Entry</div>
          <strong>本地开发入口</strong>
          <p class="muted">仅用于当前阶段的页面验证和流程联调，默认保持隐藏。</p>
          <div class="toolbar top-gap">
            <el-button type="primary" plain :loading="authStore.loading" @click="handleDeveloperEntry">
              进入开发模式
            </el-button>
          </div>
        </div>

        <div class="landing-login-footer">
          <div>
            <strong>还没有账号？</strong>
            <p>先创建账号，再回来从一条熟悉的视频开始试，会更容易上手。</p>
          </div>
          <RouterLink to="/register" class="landing-inline-link">前往注册</RouterLink>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { ElMessage } from "element-plus";
import { computed, reactive, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";

import { useAuthStore } from "../stores/auth";
import { notifyError } from "../utils/error";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const scenes = [
  {
    key: "review",
    index: "01",
    label: "课程复盘",
    short: "看完一节课，马上抓住重点",
    summary: "上完一节课后，不用再回去来回拖进度条。先把重点找出来，再顺着关键问题继续往下问。",
    points: ["先找重点", "再问细节", "内容不容易忘"],
    badge: "复盘模式",
    floatTitle: "先把一条视频里最值得留下的内容抓出来，再往下问。",
    search: "想问这节课的什么重点？",
    tags: ["重点提炼", "继续追问", "学习复盘"],
    focusCards: [
      { title: "先看重点", text: "先拿到这条视频最值得记住的几个结论。", muted: false },
      { title: "再补细节", text: "接着问某个方法怎么用、适合谁、哪里最容易忽略。", muted: true },
    ],
    cardText: "看完课程或知识类视频后，把重点、方法和例子更快留下来。",
    questionTitle: "如果你刚看完一条课程视频，可以直接这样问",
    examples: [
      {
        title: "先看重点",
        prompt: "这节课最值得先记住的 3 个重点是什么？",
        answer: "可以先抓住核心结论，再继续追问每个重点分别适合什么场景、怎么用。",
      },
      {
        title: "拆开方法",
        prompt: "视频里提到的方法，分别适合什么场景？",
        answer: "先把方法拆开来看，再结合场景去问它为什么有效、什么时候不适合用。",
      },
      {
        title: "补上遗漏",
        prompt: "如果我只有 10 分钟，最值得优先回顾哪几个部分？",
        answer: "可以先优先回顾决定理解框架的部分，再看最容易遗漏的例子和提醒。",
      },
    ],
    benefits: [
      { title: "重点先出来", text: "不用再从头翻整条视频找结论。" },
      { title: "问题越问越细", text: "同一个主题可以顺着继续追问下去。" },
      { title: "内容留得住", text: "以后再回来看，也不用重新从零开始。" },
    ],
    loginTitle: "适合从最近刚看完的一条课程视频开始",
    loginTip: "先导入一条你还有印象的视频，再从一个你真正想搞清楚的问题开始问。",
  },
  {
    key: "insight",
    index: "02",
    label: "快速抓重点",
    short: "信息很多时，先提炼最值钱的观点",
    summary: "有些视频信息量很大，但真正值得记住的并不多。先抓观点和判断，再决定哪些部分值得继续深看。",
    points: ["先抓观点", "看清判断", "再补例子"],
    badge: "提炼模式",
    floatTitle: "先把最重要的观点提炼出来，后面才不会被大量信息拖住。",
    search: "这条视频最重要的观点是什么？",
    tags: ["观点提炼", "判断依据", "信息筛选"],
    focusCards: [
      { title: "先抓主张", text: "快速知道这条视频到底想表达什么。", muted: false },
      { title: "再看依据", text: "继续追问这个判断靠什么成立、有什么例子支撑。", muted: true },
    ],
    cardText: "当视频信息很多时，先抓住最重要的观点和判断，别被细枝末节拖住。",
    questionTitle: "如果你想先抓观点，可以直接这样问",
    examples: [
      {
        title: "看主张",
        prompt: "这条视频最核心的观点是什么？",
        answer: "可以先把观点一句话说清楚，再继续问它为什么成立、依据是什么。",
      },
      {
        title: "看依据",
        prompt: "作者是用哪些例子来支撑这个判断的？",
        answer: "先把例子和理由拉出来，再判断这个观点是不是值得继续信和继续用。",
      },
      {
        title: "看边界",
        prompt: "这个结论适合什么情况，不适合什么情况？",
        answer: "问清边界之后，视频里的判断才更容易真正转成可用信息。",
      },
    ],
    benefits: [
      { title: "少看无关信息", text: "先抓到值得记的东西，再决定要不要继续深挖。" },
      { title: "更快看清逻辑", text: "观点、依据和例子会更容易串起来。" },
      { title: "判断更轻松", text: "值不值得继续看，很快就能有答案。" },
    ],
    loginTitle: "适合想快速提炼观点的时候",
    loginTip: "先从“这条视频最核心的观点是什么”这种问题开始，通常最容易得到有用结果。",
  },
  {
    key: "allVideos",
    index: "03",
    label: "全部视频问答",
    short: "把已导入的视频放在一起继续找答案",
    summary: "当你已经导入了多条视频，就不用只盯着一条问。可以直接在自己的全部资料里继续找答案、做比较、查共性。",
    points: ["跨视频查找", "放在一起比较", "越积累越好用"],
    badge: "全部视频",
    floatTitle: "视频越多，放在一起问的价值就越明显。",
    search: "在我导入的全部视频里继续找答案",
    tags: ["全部视频", "跨视频提问", "个人知识库"],
    focusCards: [
      { title: "先找共性", text: "看看不同视频里反复提到的重点和方法。", muted: false },
      { title: "再做比较", text: "把不同视频里的说法放在一起看，会更容易发现差异。", muted: true },
    ],
    cardText: "当视频慢慢积累起来后，可以直接在全部资料范围里继续问和继续查。",
    questionTitle: "如果你已经导入了多条视频，可以直接这样问",
    examples: [
      {
        title: "找共性",
        prompt: "我导入过的这些视频里，哪些内容都反复提到了“选题判断”？",
        answer: "可以先把共通点汇总出来，再继续问不同视频各自强调了哪些不一样的角度。",
      },
      {
        title: "看差异",
        prompt: "这几条视频对同一个问题的说法，有哪些明显区别？",
        answer: "放在一起问的时候，最容易看到不同作者的侧重点和判断差别。",
      },
      {
        title: "做沉淀",
        prompt: "把这些视频里关于增长策略的共通方法整理给我。",
        answer: "可以先汇总共通方法，再继续追问哪些更适合你的具体场景。",
      },
    ],
    benefits: [
      { title: "资料能串起来用", text: "不用只围着一条视频打转。" },
      { title: "更适合长期积累", text: "视频越多，这种用法越能体现价值。" },
      { title: "个人知识库更成型", text: "问到最后，不只是答案，而是你自己的资料沉淀。" },
    ],
    loginTitle: "适合已经导入过多条视频的时候",
    loginTip: "如果你已经积累了一些资料，登录后可以直接试试全部视频范围问答。",
  },
];

const activeSceneKey = ref(scenes[0].key);
const activeExampleIndex = ref(0);
const activeScene = computed(() => scenes.find((scene) => scene.key === activeSceneKey.value) ?? scenes[0]);
const activeExample = computed(() => activeScene.value.examples[activeExampleIndex.value] ?? activeScene.value.examples[0]);

watch(activeSceneKey, () => {
  activeExampleIndex.value = 0;
});

function switchScene(key) {
  activeSceneKey.value = key;
}

const inlineError = ref("");
const form = reactive({
  username: "",
  password: "",
});

async function handleSubmit() {
  inlineError.value = "";

  if (!form.username || !form.password) {
    inlineError.value = "用户名和密码不能为空。";
    return;
  }

  try {
    await authStore.login(form);
    ElMessage.success("登录成功");
    await router.push(String(route.query.redirect || "/videos"));
  } catch (error) {
    inlineError.value = notifyError(error).message;
  }
}

async function handleDeveloperEntry() {
  inlineError.value = "";
  try {
    await authStore.enterDeveloperMode();
    ElMessage.success("已进入开发模式");
    await router.push(String(route.query.redirect || "/videos"));
  } catch (error) {
    inlineError.value = notifyError(error).message;
  }
}
</script>
