(function attachSohuPlatform(global) {
  const ACCOUNT_URL = 'https://mp.sohu.com/mpfe/v4/personalCenter/account?curTab=1'
  const PUBLISH_URL = 'https://mp.sohu.com/mpfe/v4/contentManagement/news/addarticle?contentStatus=1'
  const WORKS_LIST_URL = 'https://mp.sohu.com/mpfe/v4/contentManagement/first/page'
  const STATEMENTS = ['无需声明', '含有虚构演绎内容', '含有AI生成内容', '含有营销信息', '内容为转载', '内容为个人观点']

  function isAccountUrl(value) {
    try {
      const url = new URL(value)
      return url.protocol === 'https:' && url.hostname === 'mp.sohu.com'
        && url.pathname.replace(/\/$/, '') === '/mpfe/v4/personalCenter/account'
    } catch (_) { return false }
  }

  function isPublishUrl(value) {
    try {
      const url = new URL(value)
      return url.protocol === 'https:' && url.hostname === 'mp.sohu.com'
        && url.pathname === '/mpfe/v4/contentManagement/news/addarticle'
    } catch (_) { return false }
  }

  function visible(element) {
    if (!element?.getBoundingClientRect) return false
    const style = global.getComputedStyle(element)
    const rect = element.getBoundingClientRect()
    return style.display !== 'none' && style.visibility !== 'hidden'
      && Number(style.opacity) !== 0 && rect.width > 0 && rect.height > 0
  }

  function isWorksManageUrl(value) {
    try {
      const url = new URL(value)
      return url.protocol === 'https:' && url.hostname === 'mp.sohu.com'
        && url.pathname === '/mpfe/v4/contentManagement/first/page'
    } catch (_) { return false }
  }

  function readIdentity() {
    const accountNames = new Set()
    const accountIds = new Set()
    if (location.hostname === 'mp.sohu.com' && (isAccountUrl(location.href) || isPublishUrl(location.href) || isWorksManageUrl(location.href))) {
      for (const element of document.querySelectorAll('#header-user .user-name, #account-info .account-info-text h3')) {
        if (!visible(element)) continue
        const name = String(element.textContent || '').normalize('NFKC').trim().replace(/\s+/g, ' ')
        if (name) accountNames.add(name)
      }
      const profileHref = document.querySelector('#header-user .user-head a[href*="/profile?"]')?.getAttribute('href')
      if (profileHref) {
        const xpt = new URL(profileHref, location.href).searchParams.get('xpt')
        if (xpt) accountIds.add(xpt)
      }
    }
    return {
      implemented: true,
      accountNames: Array.from(accountNames),
      accountIds: Array.from(accountIds),
      diagnostics: `href=${location.href}; source=sohu_account_header; nameCandidates=${accountNames.size}`,
    }
  }

  function requireIdentity(payload) {
    const identity = readIdentity()
    const policy = global.__GEO_IDENTITY_POLICY__?.evaluateExpectedIdentity
    if (!policy) throw new Error('SOHU_IDENTITY_POLICY_MISSING：账号校验策略未加载')
    const decision = policy({ platform: 'sohu', expectedName: payload.expectedAccountName, currentNames: identity.accountNames })
    if (!decision.matched) throw new Error(`${decision.code}：${decision.message}`)
    return identity
  }

  function prepareContent(html) {
    const template = document.createElement('template')
    template.innerHTML = html
    const images = Array.from(template.content.querySelectorAll('img')).map((image, index) => {
      const src = image.getAttribute('src') || image.getAttribute('data-src') || ''
      let url
      try { url = new URL(src) } catch (_) {}
      if (!url || !['https:', 'http:'].includes(url.protocol)) {
        throw new Error('SOHU_IMAGE_URL_INVALID：正文插图须使用可下载的图片地址')
      }
      const marker = `〔插图${Date.now()}-${index + 1}待上传〕`
      image.replaceWith(document.createTextNode(marker))
      return { url: url.href, marker }
    })
    return { html: template.innerHTML, images }
  }

  function isUploadedContentImage(image) {
    let url
    try { url = new URL(image.src, location.href) } catch (_) { return false }
    return url.protocol === 'https:' && (url.hostname === 'res.mp.sohu.com' || url.hostname.endsWith('.itc.cn'))
      && image.complete && image.naturalWidth > 0
  }

  async function fillContentImages(plan, payload, deps) {
    if (!plan.images.length) return { uploadedCount: 0 }
    requireIdentity(payload)
    const files = []
    for (const image of plan.images) {
      const file = await deps.fetchImageAsFile(image.url)
      if (!/^image\/(jpeg|jpg|png|gif)$/.test(file.type) || !file.size || file.size > 10 * 1024 * 1024) {
        throw new Error('SOHU_IMAGE_FILE_INVALID：搜狐正文插图须为 JPG、PNG 或 GIF，且不超过10MB')
      }
      files.push(file)
    }
    const editor = document.querySelector('#editor .ql-editor')
    for (let index = 0; index < plan.images.length; index += 1) {
      requireIdentity(payload)
      const marker = plan.images[index].marker
      const walker = document.createTreeWalker(editor, NodeFilter.SHOW_TEXT)
      let node
      while ((node = walker.nextNode()) && !node.textContent.includes(marker)) {}
      if (!node) throw new Error('SOHU_IMAGE_POSITION_MISSING：未找到正文插图位置，已阻止发布')
      editor.focus()
      const range = document.createRange()
      const offset = node.textContent.indexOf(marker)
      range.setStart(node, offset)
      range.setEnd(node, offset + marker.length)
      const selection = global.getSelection()
      selection.removeAllRanges()
      selection.addRange(range)
      if (!document.execCommand('delete', false)) throw new Error('SOHU_IMAGE_POSITION_MISSING：无法定位正文插图')
      const before = new Set(editor.querySelectorAll('img'))
      const button = Array.from(document.querySelectorAll('.ql-toolbar .ql-image')).find(visible)
      if (!button) throw new Error('SOHU_IMAGE_UPLOAD_ENTRY_MISSING：未找到正文添加图片入口')
      button.click()
      const panel = await deps.waitForCondition(() => Array.from(document.querySelectorAll('.local-upload')).find(visible), 5000, 'SOHU_IMAGE_UPLOAD_ENTRY_MISSING：图片上传窗口未打开')
      const input = panel.querySelector('input#new-file[type="file"]')
      if (!input) throw new Error('SOHU_IMAGE_UPLOAD_ENTRY_MISSING：未找到正文图片文件框')
      const transfer = new DataTransfer()
      transfer.items.add(files[index])
      input.files = transfer.files
      input.dispatchEvent(new Event('change', { bubbles: true }))
      const confirm = await deps.waitForCondition(() => {
        const action = panel.querySelector('.bottom-buttons .positive-button')
        const thumbnails = Array.from(panel.querySelectorAll('img.upload-item'))
        return visible(action) && !action.classList.contains('disable-button') && thumbnails.length === 1
          && thumbnails.every(isUploadedContentImage) && action
      }, 30000, 'SOHU_IMAGE_UPLOAD_NOT_CONFIRMED：未确认正文图片上传完成，已阻止发布')
      confirm.click()
      await deps.waitForCondition(() => Array.from(editor.querySelectorAll('img')).some(image => !before.has(image) && isUploadedContentImage(image)), 15000, 'SOHU_IMAGE_UPLOAD_NOT_CONFIRMED：上传图片未进入正文，已阻止发布')
    }
    if (plan.images.some(image => editor.textContent.includes(image.marker))
      || editor.querySelectorAll('img').length !== plan.images.length) {
      throw new Error('SOHU_IMAGE_UPLOAD_NOT_CONFIRMED：正文图片数量或位置未确认，已阻止发布')
    }
    return { uploadedCount: plan.images.length }
  }

  function readPublishResult(payload) {
    const identity = requireIdentity(payload)
    if (!isWorksManageUrl(location.href)) return { found: false, verified: false }
    const title = String(payload.title || payload.articleTitle || '').trim()
    if (!title) return { found: false, verified: false }
    let rows = Array.from(document.querySelectorAll('.article-content-info'))
      .filter(row => visible(row) && row.querySelector('h3.title a')?.textContent.trim() === title)
    if (payload.requireSubmissionAttempt) {
      let attempt
      try { attempt = JSON.parse(sessionStorage.getItem(`geo:sohu:submission:${identity.accountNames[0]}:${title}`)) } catch (_) {}
      const attemptedAt = Date.parse(attempt?.attemptedAt)
      const attemptedMinute = Math.floor(attemptedAt / 60000) * 60000
      if (!Number.isFinite(attemptedMinute)) {
        return { found: false, verified: false, reason: 'submission_time_not_confirmed' }
      }
      rows = rows.filter(row => {
        const date = row.querySelector('.date-common')?.textContent.trim()
        const recordedAt = Date.parse(`${date?.replace(' ', 'T')}:00+08:00`)
        return Number.isFinite(recordedAt) && recordedAt >= attemptedMinute
          && recordedAt - attemptedMinute <= 5 * 60000
          && !/草稿/.test(row.querySelector('.status')?.textContent || '')
      })
    }
    if (rows.length !== 1) return { found: false, verified: false, candidateCount: rows.length }
    const row = rows[0]
    const recordDate = row.querySelector('.date-common')?.textContent.trim() || null
    const link = row.querySelector('h3.title a')
    const url = new URL(link.getAttribute('href'), location.href)
    const status = row.querySelector('.status')?.textContent || ''
    const published = url.protocol === 'https:' && url.hostname === 'www.sohu.com'
      && /^\/a\/\d+_\d+$/.test(url.pathname) && !/审核中|未通过|审核失败|草稿/.test(status)
    const platformStatus = /审核中/.test(status) ? 'reviewing' : /未通过|审核失败/.test(status) ? 'rejected' : published ? 'published' : 'unknown'
    return {
      found: true,
      submitted: platformStatus !== 'unknown',
      submissionVerified: ['reviewing', 'published'].includes(platformStatus),
      verified: published,
      platformStatus,
      platformPublishedUrl: published ? url.href : null,
      platformPublishId: published ? url.pathname.match(/\/a\/(\d+)_/)[1] : url.searchParams.get('id'),
      previewUrl: url.href,
      title,
      recordDate,
      method: 'sohu_works_list',
    }
  }

  async function fillPublishOptions(payload, deps = {}) {
    if (!isPublishUrl(location.href)) throw new Error('SOHU_EDITOR_NOT_FOUND：当前页面不是搜狐文章编辑页')
    requireIdentity(payload)
    const options = { ...payload.profile?.platformOptions?.sohu, ...payload.platformOptions?.sohu, ...payload.sohuOptions }
    const statement = options.creationStatement || '含有AI生成内容'
    if (!STATEMENTS.includes(statement)) throw new Error('SOHU_STATEMENT_INVALID：请选择有效的搜狐创作声明')
    const radio = Array.from(document.querySelectorAll('.info-radio-label'))
      .find(element => visible(element) && element.textContent.trim() === statement)
    if (!radio) throw new Error('SOHU_STATEMENT_NOT_FOUND：未找到搜狐创作声明')
    if (!radio.classList.contains('is-checked')) radio.click()
    await (deps.delay || delay)(150)
    if (!radio.classList.contains('is-checked') && !radio.querySelector('input')?.checked) {
      throw new Error('SOHU_STATEMENT_NOT_CONFIRMED：创作声明选择未生效')
    }
    const summary = options.summary || payload.summary
    if (summary) {
      const input = document.querySelector('textarea.abstract-main-textarea')
      if (!input) throw new Error('SOHU_SUMMARY_NOT_FOUND：未找到摘要输入框')
      const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(input), 'value')?.set
      if (setter) setter.call(input, String(summary).slice(0, 120))
      else input.value = String(summary).slice(0, 120)
      input.dispatchEvent(new Event('input', { bubbles: true }))
    }
    if (payload.autoPublish !== true) return { filled: true, creationStatement: statement, message: `创作声明：${statement}，待核对发布` }
    return { filled: true, creationStatement: statement, ...(await submitOnce(payload, deps)) }
  }

  async function submitOnce(payload, deps = {}) {
    const identity = requireIdentity(payload)
    const title = String(payload.title || payload.articleTitle || '').trim()
    const actualTitle = document.querySelector('.publish-title input')?.value?.trim()
    if (Array.from(title).length < 5 || Array.from(title).length > 72 || actualTitle !== title) {
      throw new Error('SOHU_TITLE_NOT_CONFIRMED：标题须为5至72字，且读回结果须与任务一致')
    }
    if (!document.querySelector('#editor .ql-editor')?.textContent?.trim()) throw new Error('SOHU_CONTENT_EMPTY：正文未写入')
    const remainingMatch = String(document.body?.innerText || '').match(/您今天还能发\s*(\d+)\s*篇文章/)
    if (!remainingMatch) throw new Error('SOHU_DAILY_QUOTA_NOT_CONFIRMED：未读到今日剩余发文额度，已阻止发布')
    if (Number(remainingMatch[1]) <= 0) throw new Error('SOHU_DAILY_LIMIT_REACHED：今日5篇发文额度已用完，请次日再发布')
    const contentImages = Array.from(document.querySelectorAll('#editor .ql-editor img'))
    if (contentImages.some(image => !isUploadedContentImage(image))) {
      throw new Error('SOHU_IMAGE_UPLOAD_NOT_CONFIRMED：正文存在未上传成功的插图，已阻止发布')
    }
    const key = `geo:sohu:submission:${identity.accountNames[0]}:${title}`
    if (sessionStorage.getItem(key)) throw new Error('SOHU_ALREADY_SUBMITTED：已尝试提交该文章，请先人工核对我的内容，避免重复发文')
    const button = Array.from(document.querySelectorAll('.publish-report-btn'))
      .find(element => visible(element) && element.textContent.trim() === '发布')
    if (!button) throw new Error('SOHU_PUBLISH_BUTTON_NOT_FOUND：未找到搜狐发布按钮')
    if (deps.reserveSubmissionAttempt) await deps.reserveSubmissionAttempt(payload)
    sessionStorage.setItem(key, JSON.stringify({ status: 'unconfirmed', attemptedAt: new Date().toISOString() }))
    if (deps.clickTrustedActionOnce) await deps.clickTrustedActionOnce(button, { platform: 'sohu', label: '搜狐发布' })
    else button.click()
    for (let attempt = 0; attempt < 40; attempt += 1) {
      await (deps.delay || delay)(500)
      if (isWorksManageUrl(location.href)) {
        const result = readPublishResult({ ...payload, requireSubmissionAttempt: true })
        if (result.platformStatus === 'rejected') throw new Error('SOHU_REVIEW_REJECTED：搜狐文章审核未通过')
        if (result.submissionVerified) {
          return { submitted: true, published: result.verified,
            platformPublishedUrl: result.platformPublishedUrl,
            message: result.verified ? '搜狐文章已公开发布' : '搜狐文章已提交，审核中',
            publishVerification: result }
        }
      }
      const errors = Array.from(document.querySelectorAll('.el-message--error, .el-message--warning')).filter(visible)
      if (errors.length) {
        const message = errors.map(element => element.textContent.trim()).join('；')
        // A definite validation rejection permits correction; unknown submission never retries.
        if (/标题|正文|封面|声明/.test(message)) sessionStorage.removeItem(key)
        throw new Error(`SOHU_PUBLISH_REJECTED：${message}`)
      }
      const success = Array.from(document.querySelectorAll('.el-message--success, .el-dialog, [class*="success"]'))
        .filter(visible).find(element => /发布成功|提交成功|审核中/.test(element.textContent || ''))
      if (success) {
        return {
          submitted: true,
          published: false,
          message: '搜狐已提交，需在我的内容核对审核及发布结果',
          publishVerification: { verified: false, platformStatus: 'submitted', method: 'sohu_submission_signal', url: location.href },
        }
      }
    }
    throw new Error('SOHU_PUBLISH_NOT_CONFIRMED：未确认提交结果，请先核对我的内容，禁止重复提交')
  }

  function delay(ms) { return new Promise(resolve => setTimeout(resolve, ms)) }

  global.__GEO_SOHU_PLATFORM__ = {
    ACCOUNT_URL, PUBLISH_URL, WORKS_LIST_URL, isAccountUrl, isPublishUrl, isWorksManageUrl, readIdentity, readPublishResult,
    prepareContent, fillContentImages, isUploadedContentImage,
    editorSelectors: () => ({ title: ['.publish-title input[placeholder*="标题"]'], content: ['#editor .ql-editor[contenteditable="true"]'], tags: [] }),
    createPublishOptionsAdapter: deps => ({ platform: 'sohu', fillPublishOptions: payload => fillPublishOptions(payload, deps) }),
  }
})(globalThis)
