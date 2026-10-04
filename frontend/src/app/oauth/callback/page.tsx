'use client';

import { Suspense, useEffect, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Center } from '@astryxdesign/core/Center';
import { VStack } from '@astryxdesign/core/Stack';
import { Heading, Text } from '@astryxdesign/core/Text';
import { Banner } from '@astryxdesign/core/Banner';
import { useAuth } from '@/lib/auth-context';

function OAuthCallback() {
  const params = useSearchParams();
  const router = useRouter();
  const { completeOAuthLogin } = useAuth();
  const started = useRef(false);
  const code = params.get('code');
  const [error, setError] = useState<string | null>(
    code ? null : 'Google 로그인 인증 코드가 없습니다. 다시 시도해 주세요.',
  );

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    if (!code) return;
    completeOAuthLogin(code)
      .then(() => router.replace('/review'))
      .catch(() => setError('Google 로그인에 실패했습니다. 다시 시도해 주세요.'));
  }, [code, completeOAuthLogin, router]);

  return (
    <Center height="100vh">
      <VStack gap={3} width={360}>
        <Heading level={3}>Google 로그인</Heading>
        {error ? <Banner status="error" title="로그인 실패" description={error} /> : <Text>로그인을 완료하는 중입니다...</Text>}
      </VStack>
    </Center>
  );
}

export default function OAuthCallbackPage() {
  return <Suspense fallback={<Center height="100vh"><Text>로그인을 준비하는 중입니다...</Text></Center>}><OAuthCallback /></Suspense>;
}
