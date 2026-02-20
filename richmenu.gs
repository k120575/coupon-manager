/**
 * Rich Menu 設定腳本
 * 
 * 使用方式：
 *   1. 先在 GAS 執行 setupRichMenu() 函式
 *   2. 函式會自動建立 Rich Menu 並設為預設
 *   3. 圖片需要手動上傳（見下方 uploadRichMenuImage 說明）
 * 
 * Rich Menu 佈局: 3x2 格
 *   ┌──────────┬──────────┬──────────┐
 *   │ 📋 查詢  │ 🏷️ 分類  │ ➕ 記錄  │
 *   ├──────────┼──────────┼──────────┤
 *   │ ✅ 使用  │ 🗑️ 刪除  │ ❓ 幫助  │
 *   └──────────┴──────────┴──────────┘
 */

/** 建立 Rich Menu 並設為預設（Step 1） */
function setupRichMenu() {
  const richMenu = {
    'size': { 'width': 2500, 'height': 1686 },
    'selected': true,
    'name': '優惠券管家主選單',
    'chatBarText': '📋 開啟選單',
    'areas': [
      // Row 1
      {
        'bounds': { 'x': 0, 'y': 0, 'width': 833, 'height': 843 },
        'action': { 'type': 'message', 'text': '📋 查詢票券' }
      },
      {
        'bounds': { 'x': 833, 'y': 0, 'width': 834, 'height': 843 },
        'action': { 'type': 'message', 'text': '🏷️ 分類查詢' }
      },
      {
        'bounds': { 'x': 1667, 'y': 0, 'width': 833, 'height': 843 },
        'action': { 'type': 'message', 'text': '➕ 記錄優惠券' }
      },
      // Row 2
      {
        'bounds': { 'x': 0, 'y': 843, 'width': 833, 'height': 843 },
        'action': { 'type': 'message', 'text': '✅ 使用票券' }
      },
      {
        'bounds': { 'x': 833, 'y': 843, 'width': 834, 'height': 843 },
        'action': { 'type': 'message', 'text': '🗑️ 刪除票券' }
      },
      {
        'bounds': { 'x': 1667, 'y': 843, 'width': 833, 'height': 843 },
        'action': { 'type': 'message', 'text': '❓ 幫助' }
      }
    ]
  };

  const res = UrlFetchApp.fetch('https://api.line.me/v2/bot/richmenu', {
    'headers': { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + CHANNEL_ACCESS_TOKEN },
    'method': 'post',
    'payload': JSON.stringify(richMenu)
  });

  const richMenuId = JSON.parse(res.getContentText()).richMenuId;
  console.log('✅ Rich Menu 建立成功！ID: ' + richMenuId);
  console.log('👉 接下來請執行 uploadRichMenuImage("' + richMenuId + '") 上傳圖片');

  // 存到 Script Properties 方便後續使用
  scriptProps.setProperty('RICH_MENU_ID', richMenuId);
  return richMenuId;
}

/**
 * 上傳 Rich Menu 圖片（Step 2）
 * 需要一張 2500x1686 的 PNG/JPEG 圖片
 * 
 * 使用方式：
 *   1. 把圖片放到 Google Drive
 *   2. 取得檔案 ID（從分享連結中提取）
 *   3. 執行 uploadRichMenuImageFromDrive("你的richMenuId", "你的driveFileId")
 */
function uploadRichMenuImageFromDrive(richMenuId, driveFileId) {
  if (!richMenuId) richMenuId = scriptProps.getProperty('RICH_MENU_ID');
  if (!richMenuId) { console.log('❌ 沒有 Rich Menu ID，請先執行 setupRichMenu()'); return; }

  const file = DriveApp.getFileById(driveFileId);
  const blob = file.getBlob();

  UrlFetchApp.fetch(`https://api-data.line.me/v2/bot/richmenu/${richMenuId}/content`, {
    'headers': { 'Content-Type': blob.getContentType(), 'Authorization': 'Bearer ' + CHANNEL_ACCESS_TOKEN },
    'method': 'post',
    'payload': blob.getBytes()
  });

  console.log('✅ 圖片上傳成功！');
  console.log('👉 接下來請執行 setDefaultRichMenu() 設為預設');
}

/** 設為所有使用者的預設 Rich Menu（Step 3） */
function setDefaultRichMenu(richMenuId) {
  if (!richMenuId) richMenuId = scriptProps.getProperty('RICH_MENU_ID');
  if (!richMenuId) { console.log('❌ 沒有 Rich Menu ID，請先執行 setupRichMenu()'); return; }

  UrlFetchApp.fetch(`https://api.line.me/v2/bot/user/all/richmenu/${richMenuId}`, {
    'headers': { 'Authorization': 'Bearer ' + CHANNEL_ACCESS_TOKEN },
    'method': 'post'
  });

  console.log('✅ Rich Menu 已設為所有使用者預設！');
}

/** 一鍵完成：建立 + 上傳 + 設預設（圖片需先上傳到 Google Drive） */
function setupRichMenuAll(driveFileId) {
  if (!driveFileId) {
    console.log('❌ 請提供 Google Drive 圖片檔案 ID');
    console.log('用法：setupRichMenuAll("你的driveFileId")');
    return;
  }
  const richMenuId = setupRichMenu();
  uploadRichMenuImageFromDrive(richMenuId, driveFileId);
  setDefaultRichMenu(richMenuId);
  console.log('🎉 Rich Menu 設定完成！');
}

/** 刪除目前的 Rich Menu */
function deleteCurrentRichMenu() {
  const richMenuId = scriptProps.getProperty('RICH_MENU_ID');
  if (!richMenuId) { console.log('沒有已儲存的 Rich Menu ID'); return; }
  UrlFetchApp.fetch(`https://api.line.me/v2/bot/richmenu/${richMenuId}`, {
    'headers': { 'Authorization': 'Bearer ' + CHANNEL_ACCESS_TOKEN },
    'method': 'delete'
  });
  scriptProps.deleteProperty('RICH_MENU_ID');
  console.log('✅ Rich Menu 已刪除');
}

/** 列出所有 Rich Menu */
function listRichMenus() {
  const res = UrlFetchApp.fetch('https://api.line.me/v2/bot/richmenu/list', {
    'headers': { 'Authorization': 'Bearer ' + CHANNEL_ACCESS_TOKEN },
    'method': 'get'
  });
  const menus = JSON.parse(res.getContentText()).richmenus;
  console.log(`找到 ${menus.length} 個 Rich Menu:`);
  menus.forEach(m => console.log(`  ID: ${m.richMenuId} | Name: ${m.name}`));
  return menus;
}
