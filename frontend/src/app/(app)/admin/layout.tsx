'use client';

import { usePathname, useRouter } from 'next/navigation';
import { VStack } from '@astryxdesign/core/Stack';
import { Section } from '@astryxdesign/core/Section';
import { Heading } from '@astryxdesign/core/Text';
import { TabList, Tab } from '@astryxdesign/core/TabList';
import { RequireAdmin } from '@/components/RequireAdmin';

const TABS = [
  { value: '/admin/symbols', label: '종목 관리' },
  { value: '/admin/users', label: '유저 관리' },
  { value: '/admin/collector', label: '수집기 상태' },
];

function AdminNav({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const activeTab = TABS.find((tab) => pathname.startsWith(tab.value))?.value ?? TABS[0].value;

  return (
    <VStack gap={0}>
      <Section padding={4} dividers={['bottom']}>
        <VStack gap={4}>
          <Heading level={3}>관리자</Heading>
          <TabList value={activeTab} onChange={(value) => router.push(value)}>
            {TABS.map((tab) => (
              <Tab key={tab.value} value={tab.value} label={tab.label} />
            ))}
          </TabList>
        </VStack>
      </Section>
      <Section padding={4}>{children}</Section>
    </VStack>
  );
}

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireAdmin>
      <AdminNav>{children}</AdminNav>
    </RequireAdmin>
  );
}
