// 액세스 토큰이 만료됐을 때 로그인 화면에서 세션을 복원한다.
(function () {
    const originalFetch = window.fetch.bind(window);
    let notified = false;

    window.fetch = async function (...args) {
        const response = await originalFetch(...args);
        const url = new URL(response.url || '/', window.location.origin);
        if (response.status === 401 && url.origin === window.location.origin
                && url.pathname.startsWith('/api/') && !notified) {
            notified = true;
            window.location.replace('/login');
        }
        return response;
    };
})();
