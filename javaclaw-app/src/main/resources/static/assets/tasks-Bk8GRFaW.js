import{c as s,H as a,h as o}from"./index-D74xtTzq.js";/**
 * @license @tabler/icons-react v3.41.1 - MIT
 *
 * This source code is licensed under the MIT license.
 * See the LICENSE file in the root directory of this source tree.
 */const c=[["path",{d:"M12 3a9 9 0 1 0 9 9",key:"svg-0"}]],r=s("outline","loader-2","Loader2",c);/**
 * @license @tabler/icons-react v3.41.1 - MIT
 *
 * This source code is licensed under the MIT license.
 * See the LICENSE file in the root directory of this source tree.
 */const i=[["path",{d:"M5 7a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2l0 -10",key:"svg-0"}]],u=s("outline","player-stop","PlayerStop",i);function l(t){const e=new URLSearchParams;t!=null&&t.userId&&e.set("userId",t.userId),t!=null&&t.status&&e.set("status",t.status);const n=e.toString();return o(`/api/tasks${n?`?${n}`:""}`,{method:"GET"})}function h(t){return o(`/api/tasks/${encodeURIComponent(t)}`,{method:"GET"})}function k(t){return o(`/api/tasks/${encodeURIComponent(t)}/children`,{method:"GET"})}async function p(t){const e=await a(`/api/tasks/${encodeURIComponent(t)}/cancel`,{method:"POST"});if(!e.ok)throw new Error(`Failed to cancel task: ${e.status}`)}async function I(t){return o(`/api/tasks/${encodeURIComponent(t)}`,{method:"DELETE"})}export{r as I,k as a,u as b,p as c,I as d,h as g,l};
