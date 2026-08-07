import { useState, type FormEvent, type PropsWithChildren } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { Alert, Button, LinkButton } from "../../components/ui";
import { ApiError, demoFallbackEnabled } from "../../lib/api";
import type { UserResponse } from "../../types";

const DEMO_PASSWORD = "Demo1234!";
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type LoginValues = {
  email: string;
  password: string;
};

type LoginErrors = Partial<Record<keyof LoginValues, string>>;

type RegisterValues = {
  name: string;
  email: string;
  phone: string;
  password: string;
  confirmPassword: string;
};

type RegisterField = keyof RegisterValues | "agreement";
type RegisterErrors = Partial<Record<RegisterField, string>>;

function loginFieldError(field: keyof LoginValues, value: string) {
  if (field === "email") {
    if (!value.trim()) return "Enter your email address.";
    if (!EMAIL_PATTERN.test(value.trim())) return "Enter a valid email address.";
  }
  if (field === "password" && !value) return "Enter your password.";
  return undefined;
}

function registerFieldError(field: keyof RegisterValues, values: RegisterValues) {
  const value = values[field];
  if (field === "name") {
    if (!value.trim()) return "Enter your full name.";
    if (value.trim().length > 100) return "Name must be 100 characters or fewer.";
  }
  if (field === "email") {
    if (!value.trim()) return "Enter your email address.";
    if (!EMAIL_PATTERN.test(value.trim())) return "Enter a valid email address.";
  }
  if (field === "phone" && value.trim().length > 20) return "Phone number must be 20 characters or fewer.";
  if (field === "password") {
    if (!value) return "Create a password.";
    if (value.length < 6) return "Password must be at least 6 characters.";
    if (value.length > 100) return "Password must be 100 characters or fewer.";
  }
  if (field === "confirmPassword") {
    if (!value) return "Confirm your password.";
    if (value !== values.password) return "Passwords do not match.";
  }
  return undefined;
}

function authErrorMessage(caught: unknown, action: "login" | "register") {
  if (!(caught instanceof ApiError)) {
    return action === "login"
      ? "We could not sign you in. Please try again."
      : "We could not create your account. Please try again.";
  }

  switch (caught.code) {
    case "INVALID_CREDENTIALS":
      return "The email or password is incorrect. Please try again.";
    case "ACCOUNT_INACTIVE":
      return "This account is inactive. Please contact support before signing in.";
    case "EMAIL_EXISTS":
      return "An account already uses this email address. Try signing in instead.";
    case "NETWORK_ERROR":
      return "We could not reach the marketplace service. Check your connection and try again.";
    default:
      return caught.message || (action === "login" ? "Unable to sign in." : "Unable to create account.");
  }
}

function safeReturnPath(value: unknown) {
  if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//")) return null;
  if (value === "/login" || value === "/register") return null;
  return value;
}

function destinationFor(user: UserResponse, returnPath: unknown) {
  return safeReturnPath(returnPath) ?? (user.role === "ADMIN" ? "/admin" : "/app/client");
}

function AuthFrame({ mode, children }: PropsWithChildren<{ mode: "login" | "register" }>) {
  const isLogin = mode === "login";

  return (
    <div className="auth-shell">
      <section className="auth-art" aria-label="Service Marketplace introduction">
        <img
          src={isLogin ? "/images/home-cleaning.jpg" : "/images/moving-help.jpg"}
          alt=""
        />
        <div className="auth-quote">
          <span className="eyebrow">Chicago service marketplace</span>
          <h1>{isLogin ? "Good work starts with clear expectations." : "One account. Two ways to take part."}</h1>
          <p>
            {isLogin
              ? "Find local services, respond to opportunities, and follow each engagement from acceptance through completion."
              : "Post requests as a client, offer services as a worker, and switch workspaces whenever your day changes."}
          </p>
        </div>
      </section>
      <section className="auth-panel">{children}</section>
    </div>
  );
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [values, setValues] = useState<LoginValues>({ email: "", password: "" });
  const [errors, setErrors] = useState<LoginErrors>({});
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const update = (field: keyof LoginValues, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    setFormError(null);
  };

  const validateField = (field: keyof LoginValues) => {
    setErrors((current) => ({ ...current, [field]: loginFieldError(field, values[field]) }));
  };

  const fillDemoAccount = (email: string) => {
    setValues({ email, password: DEMO_PASSWORD });
    setErrors({});
    setFormError(null);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors: LoginErrors = {
      email: loginFieldError("email", values.email),
      password: loginFieldError("password", values.password),
    };
    setErrors(nextErrors);
    setFormError(null);
    if (Object.values(nextErrors).some(Boolean)) return;

    setSubmitting(true);
    try {
      const user = await login(values.email, values.password);
      const returnPath = (location.state as { from?: unknown } | null)?.from;
      navigate(destinationFor(user, returnPath), { replace: true });
    } catch (caught) {
      if (caught instanceof ApiError && (caught.field === "email" || caught.field === "password")) {
        setErrors((current) => ({ ...current, [caught.field as keyof LoginValues]: caught.message }));
      }
      setFormError(authErrorMessage(caught, "login"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthFrame mode="login">
      <form className="auth-form" noValidate onSubmit={handleSubmit} aria-busy={submitting}>
        <div>
          <span className="eyebrow">Welcome back</span>
          <h1>Sign in</h1>
          <p>Use your existing account to continue to your marketplace workspace.</p>
        </div>

        {demoFallbackEnabled && (
          <Alert tone="info" title="Demo access is ready">
            Fill a seeded account below. If the local API is offline, the same credentials open a clearly marked demo session.
          </Alert>
        )}

        {formError && <Alert tone="danger" title="Unable to sign in">{formError}</Alert>}

        {demoFallbackEnabled && (
          <>
            <div className="form-grid">
              <Button type="button" variant="secondary" disabled={submitting} onClick={() => fillDemoAccount("john@example.com")}>
                Fill member demo
              </Button>
              <Button type="button" variant="secondary" disabled={submitting} onClick={() => fillDemoAccount("admin@marketplace.com")}>
                Fill admin demo
              </Button>
            </div>
            <div className="auth-divider">or enter your account</div>
          </>
        )}

        <div className={errors.email ? "field field-error" : "field"}>
          <label htmlFor="login-email">Email</label>
          <input
            id="login-email"
            name="email"
            type="email"
            autoComplete="email"
            autoCapitalize="none"
            spellCheck={false}
            disabled={submitting}
            value={values.email}
            onChange={(event) => update("email", event.target.value)}
            onBlur={() => validateField("email")}
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? "login-email-error" : undefined}
          />
          {errors.email && <small id="login-email-error">{errors.email}</small>}
        </div>

        <div className={errors.password ? "field field-error" : "field"}>
          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            name="password"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            disabled={submitting}
            value={values.password}
            onChange={(event) => update("password", event.target.value)}
            onBlur={() => validateField("password")}
            aria-invalid={Boolean(errors.password)}
            aria-describedby={errors.password ? "login-password-error" : undefined}
          />
          {errors.password && <small id="login-password-error">{errors.password}</small>}
        </div>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={showPassword}
            disabled={submitting}
            onChange={(event) => setShowPassword(event.target.checked)}
          />
          Show password
        </label>

        <Button type="submit" busy={submitting}>
          {submitting ? "Signing in…" : "Sign in"}
        </Button>

        <div className="auth-divider">New to the marketplace?</div>
        <LinkButton to="/register" variant="secondary">Create an account</LinkButton>
      </form>
    </AuthFrame>
  );
}

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [values, setValues] = useState<RegisterValues>({
    name: "",
    email: "",
    phone: "",
    password: "",
    confirmPassword: "",
  });
  const [agreed, setAgreed] = useState(false);
  const [errors, setErrors] = useState<RegisterErrors>({});
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const update = (field: keyof RegisterValues, value: string) => {
    const nextValues = { ...values, [field]: value };
    setValues(nextValues);
    setErrors((current) => ({
      ...current,
      [field]: undefined,
      ...(field === "password" || field === "confirmPassword" ? { confirmPassword: undefined } : {}),
    }));
    setFormError(null);
  };

  const validateField = (field: keyof RegisterValues) => {
    setErrors((current) => ({ ...current, [field]: registerFieldError(field, values) }));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors: RegisterErrors = {
      name: registerFieldError("name", values),
      email: registerFieldError("email", values),
      phone: registerFieldError("phone", values),
      password: registerFieldError("password", values),
      confirmPassword: registerFieldError("confirmPassword", values),
      agreement: agreed ? undefined : "Agree to the Terms of Service and Privacy Policy to continue.",
    };
    setErrors(nextErrors);
    setFormError(null);
    if (Object.values(nextErrors).some(Boolean)) return;

    setSubmitting(true);
    try {
      const user = await register({
        name: values.name.trim(),
        email: values.email.trim(),
        password: values.password,
        phone: values.phone.trim() || undefined,
      });
      const returnPath = (location.state as { from?: unknown } | null)?.from;
      navigate(destinationFor(user, returnPath), { replace: true });
    } catch (caught) {
      if (
        caught instanceof ApiError
        && (caught.field === "name" || caught.field === "email" || caught.field === "phone" || caught.field === "password")
      ) {
        setErrors((current) => ({ ...current, [caught.field as RegisterField]: caught.message }));
      }
      setFormError(authErrorMessage(caught, "register"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthFrame mode="register">
      <form className="auth-form" noValidate onSubmit={handleSubmit} aria-busy={submitting}>
        <div>
          <span className="eyebrow">Get started</span>
          <h1>Create your account</h1>
          <p>One account can request services and provide them.</p>
        </div>

        {demoFallbackEnabled && (
          <Alert tone="info" title="Works without the local API">
            If the API is offline, registration opens a demo session only; it does not create server data.
          </Alert>
        )}

        {formError && <Alert tone="danger" title="Unable to create account">{formError}</Alert>}

        <div className={errors.name ? "field field-error" : "field"}>
          <label htmlFor="register-name">Full name</label>
          <input
            id="register-name"
            name="name"
            type="text"
            autoComplete="name"
            maxLength={100}
            disabled={submitting}
            value={values.name}
            onChange={(event) => update("name", event.target.value)}
            onBlur={() => validateField("name")}
            aria-invalid={Boolean(errors.name)}
            aria-describedby={errors.name ? "register-name-error" : undefined}
          />
          {errors.name && <small id="register-name-error">{errors.name}</small>}
        </div>

        <div className={errors.email ? "field field-error" : "field"}>
          <label htmlFor="register-email">Email</label>
          <input
            id="register-email"
            name="email"
            type="email"
            autoComplete="email"
            autoCapitalize="none"
            spellCheck={false}
            disabled={submitting}
            value={values.email}
            onChange={(event) => update("email", event.target.value)}
            onBlur={() => validateField("email")}
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? "register-email-error" : undefined}
          />
          {errors.email && <small id="register-email-error">{errors.email}</small>}
        </div>

        <div className={errors.phone ? "field field-error" : "field"}>
          <label htmlFor="register-phone">Phone <span className="muted">(optional)</span></label>
          <input
            id="register-phone"
            name="phone"
            type="tel"
            autoComplete="tel"
            maxLength={20}
            disabled={submitting}
            value={values.phone}
            onChange={(event) => update("phone", event.target.value)}
            onBlur={() => validateField("phone")}
            aria-invalid={Boolean(errors.phone)}
            aria-describedby={errors.phone ? "register-phone-error" : undefined}
          />
          {errors.phone && <small id="register-phone-error">{errors.phone}</small>}
        </div>

        <div className={errors.password ? "field field-error" : "field"}>
          <label htmlFor="register-password">Password</label>
          <input
            id="register-password"
            name="password"
            type={showPassword ? "text" : "password"}
            autoComplete="new-password"
            disabled={submitting}
            value={values.password}
            onChange={(event) => update("password", event.target.value)}
            onBlur={() => validateField("password")}
            aria-invalid={Boolean(errors.password)}
            aria-describedby={errors.password ? "register-password-error" : "register-password-hint"}
          />
          {errors.password
            ? <small id="register-password-error">{errors.password}</small>
            : <small id="register-password-hint">Use at least 6 characters.</small>}
        </div>

        <div className={errors.confirmPassword ? "field field-error" : "field"}>
          <label htmlFor="register-confirm-password">Confirm password</label>
          <input
            id="register-confirm-password"
            name="confirmPassword"
            type={showPassword ? "text" : "password"}
            autoComplete="new-password"
            disabled={submitting}
            value={values.confirmPassword}
            onChange={(event) => update("confirmPassword", event.target.value)}
            onBlur={() => validateField("confirmPassword")}
            aria-invalid={Boolean(errors.confirmPassword)}
            aria-describedby={errors.confirmPassword ? "register-confirm-password-error" : undefined}
          />
          {errors.confirmPassword && <small id="register-confirm-password-error">{errors.confirmPassword}</small>}
        </div>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={showPassword}
            disabled={submitting}
            onChange={(event) => setShowPassword(event.target.checked)}
          />
          Show passwords
        </label>

        <div className={errors.agreement ? "field-error" : ""}>
          <div className="checkbox">
            <input
              id="register-agreement"
              type="checkbox"
              checked={agreed}
              disabled={submitting}
              onChange={(event) => {
                setAgreed(event.target.checked);
                setErrors((current) => ({ ...current, agreement: undefined }));
              }}
              aria-invalid={Boolean(errors.agreement)}
              aria-describedby={errors.agreement ? "register-agreement-error" : undefined}
            />
            <label htmlFor="register-agreement">
              I agree to the <Link className="text-link" to="/terms">Terms of Service</Link> and{" "}
              <Link className="text-link" to="/privacy">Privacy Policy</Link>.
            </label>
          </div>
          {errors.agreement && <small id="register-agreement-error">{errors.agreement}</small>}
        </div>

        <Button type="submit" busy={submitting}>
          {submitting ? "Creating account…" : "Create account"}
        </Button>

        <div className="auth-divider">Already have an account?</div>
        <LinkButton to="/login" variant="secondary">Sign in</LinkButton>
      </form>
    </AuthFrame>
  );
}
