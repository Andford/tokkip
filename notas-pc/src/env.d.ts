/// <reference types="vite/client" />

declare const __APP_VERSION__: string;

declare module "*.wav" {
  const src: string;
  export default src;
}
