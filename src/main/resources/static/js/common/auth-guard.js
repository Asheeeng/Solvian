import { getCurrentUser } from './storage.js';

export function requireAuth() {
  const session = getCurrentUser();
  if (!session?.token || !session?.user) {
    window.location.replace('/login.html');
    return null;
  }
  return session;
}

export function requireGuest() {
  const session = getCurrentUser();
  if (session?.token && session?.user) {
    window.location.replace('/student-review.html');
    return false;
  }
  return true;
}

export function roleToLabel(role) {
  const map = {
    STUDENT: '学生',
    TEACHER: '老师',
    ADMIN: '管理员',
    student: '学生',
    teacher: '老师',
    admin: '管理员'
  };
  return map[role] || role;
}
