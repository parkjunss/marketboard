import { Center } from '@astryxdesign/core/Center';
import { VStack } from '@astryxdesign/core/Stack';
import { Heading, Text } from '@astryxdesign/core/Text';

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <VStack gap={2}>
      <Heading level={4}>{title}</Heading>
      <Text type="body">{children}</Text>
    </VStack>
  );
}

export default function PrivacyPage() {
  return (
    <Center axis="horizontal">
      <VStack gap={6} maxWidth={720} paddingBlock={10} paddingInline={6}>
        <VStack gap={1}>
          <Heading level={2}>개인정보 처리방침</Heading>
          <Text type="body" color="secondary">
            최종 수정일: 2026-07-20
          </Text>
        </VStack>

        <Section title="1. 수집하는 개인정보">
          회원가입 시 이메일, 비밀번호, 이름(사용자명)을 수집합니다. 비밀번호는 평문으로 저장하지 않고
          BCrypt로 해시 처리하여 저장합니다. 서비스 이용 과정에서 워치리스트, 가격 알림, 포트폴리오
          보유 종목·수량 등 직접 입력하신 데이터가 함께 저장됩니다.
        </Section>

        <Section title="2. 이용 목적">
          계정 인증(로그인)과 워치리스트·알림·포트폴리오 등 서비스 기능 제공 목적으로만 사용합니다.
          로그인 유지를 위해 JWT 액세스/리프레시 토큰을 발급하며, 리프레시 토큰은 브라우저
          localStorage에 저장됩니다.
        </Section>

        <Section title="3. 제3자 제공">
          수집한 개인정보를 제3자에게 판매하거나 제공하지 않습니다. 시세·재무 데이터는 Finnhub 등 외부
          API에서 조회해 오는 것이며, 이 과정에서 회원님의 개인정보가 외부로 전송되지 않습니다.
        </Section>

        <Section title="4. 보관 기간 및 삭제">
          회원 탈퇴(계정 삭제) 시까지 보관합니다. 계정을 삭제하면 이메일·비밀번호와 함께 워치리스트,
          알림, 포트폴리오, 대시보드 설정 등 계정에 연결된 모든 데이터가 함께 삭제되며 복구할 수
          없습니다. 현재는 별도의 탈퇴 화면이 없어 운영자에게 요청하시면 삭제해드립니다.
        </Section>

        <Section title="5. 개인 프로젝트 고지">
          MarketBoard는 개인이 학습 목적으로 만든 프로젝트로, 상업 서비스 수준의 법적 개인정보
          보호 체계(전담 인력, 외부 감사 등)를 갖추고 있지 않습니다. 민감한 실거래 계좌 정보(증권사
          연동 등)는 수집하지 않으며, 참고용으로 이용해주시기 바랍니다.
        </Section>
      </VStack>
    </Center>
  );
}
