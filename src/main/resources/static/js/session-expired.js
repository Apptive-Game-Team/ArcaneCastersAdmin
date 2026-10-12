// Every admin API call runs behind the jwt cookie, which expires after 24 hours.
// The server answers an expired call with 401, so send the person back to login
// instead of letting each page report a bare "Failed to fetch".
(function () {
    const originalFetch = window.fetch.bind(window);
    let notified = false;

    window.fetch = async function (...args) {
        const response = await originalFetch(...args);
        if (response.status === 401 && !notified) {
            notified = true;
            alert('로그인이 만료되었습니다. 다시 로그인해 주세요.');
            window.location.href = '/login';
        }
        return response;
    };
})();
