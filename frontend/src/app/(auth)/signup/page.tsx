'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import NextLink from 'next/link';
import { Center } from '@astryxdesign/core/Center';
import { VStack } from '@astryxdesign/core/Stack';
import { Card } from '@astryxdesign/core/Card';
import { Heading, Text } from '@astryxdesign/core/Text';
import { TextInput } from '@astryxdesign/core/TextInput';
import { CheckboxInput } from '@astryxdesign/core/CheckboxInput';
import { Button } from '@astryxdesign/core/Button';
import { Banner } from '@astryxdesign/core/Banner';
import { RedirectIfAuthed } from '@/components/RedirectIfAuthed';
import { useAuth } from '@/lib/auth-context';
import { ApiError } from '@/lib/api';

function SignupForm() {
  const router = useRouter();
  const { signup, login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [username, setUsername] = useState('');
  const [termsAgreed, setTermsAgreed] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const passwordMismatch = passwordConfirm.length > 0 && password !== passwordConfirm;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (password !== passwordConfirm) {
      setError('비밀번호가 일치하지 않습니다.');
      return;
    }
    if (!termsAgreed) {
      setError('약관에 동의해야 가입할 수 있습니다.');
      return;
    }

    setIsSubmitting(true);
    try {
      await signup({ email, password, passwordConfirm, username, termsAgreed });
      await login(email, password);
      router.replace('/stock-list');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '회원가입에 실패했습니다.');
      setIsSubmitting(false);
    }
  }

  return (
    <Center height="100vh">
      <VStack gap={4} width={360}>
        <VStack gap={1}>
          <Heading level={3}>MarketBoard 가입</Heading>
          <Text type="body" color="secondary">
            계정을 만들고 실시간 시세를 확인하세요
          </Text>
        </VStack>
        <Card padding={8} width="100%">
          <form onSubmit={handleSubmit}>
            <VStack gap={4}>
              {error && <Banner status="error" title="회원가입 실패" description={error} />}
              <TextInput label="이름" value={username} onChange={setUsername} isRequired hasAutoFocus />
              <TextInput type="email" label="이메일" value={email} onChange={setEmail} isRequired />
              <TextInput
                type="password"
                label="비밀번호"
                value={password}
                onChange={setPassword}
                isRequired
                description="8자 이상"
              />
              <TextInput
                type="password"
                label="비밀번호 확인"
                value={passwordConfirm}
                onChange={setPasswordConfirm}
                isRequired
                status={passwordMismatch ? { type: 'error', message: '비밀번호가 일치하지 않습니다' } : undefined}
              />
              <CheckboxInput
                label="이용약관 및 개인정보 처리방침에 동의합니다"
                value={termsAgreed}
                onChange={setTermsAgreed}
                isRequired
              />
              <Button type="submit" variant="primary" label="가입하기" isLoading={isSubmitting} />
            </VStack>
          </form>
        </Card>
        <Text type="body" size="sm">
          이미 계정이 있으신가요? <NextLink href="/login">로그인</NextLink>
        </Text>
      </VStack>
    </Center>
  );
}

export default function SignupPage() {
  return (
    <RedirectIfAuthed>
      <SignupForm />
    </RedirectIfAuthed>
  );
}
