import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { DotGothic16, Shippori_Mincho_B1 } from "next/font/google";
import "./globals.css";

const display = Shippori_Mincho_B1({
  subsets: ["latin"],
  weight: ["600", "700", "800"],
  variable: "--f-display",
  display: "swap",
});

const pixel = DotGothic16({
  subsets: ["latin"],
  weight: "400",
  variable: "--f-pixel",
  display: "swap",
});

export const metadata: Metadata = {
  title: "ABYSSAL LOOT — 深淵の略奪者 | 究極ハクスラ×ローグ",
  description:
    "实时アクション・ハック＆スラッシュとローグライク育成の融合。ランダム生成される装備、祝福、迷宮。虚無神ニヒルが待つ第25層を目指せ。",
};

export const viewport: Viewport = {
  themeColor: "#06050b",
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  viewportFit: "cover",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ja" className={`${display.variable} ${pixel.variable}`}>
      <body className="bg-abyss text-bone antialiased">{children}</body>
    </html>
  );
}
